package com.pangu.interfaces.web.service;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextLoader;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.infrastructure.persistence.mapper.AccountMapper;
import com.pangu.infrastructure.persistence.mapper.IdentityShadowMapper;
import com.pangu.infrastructure.persistence.mapper.SysMenuMapper;
import com.pangu.interfaces.security.JwtTokenProvider;
import com.pangu.interfaces.web.controller.dto.LoginRequest;
import com.pangu.interfaces.web.controller.dto.NavMenuResponse;
import com.pangu.interfaces.web.controller.dto.NavPageResponse;
import com.pangu.interfaces.web.controller.dto.SwitchShadowRequest;
import com.pangu.interfaces.web.controller.dto.SwitchTenantRequest;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 双端统一登录与多租户会话服务（M1 RBAC 重构后版本）。
 *
 * <p>登录流程：
 * <ol>
 *   <li>{@link #login} —— 手机号 + 短信验证码 → 校验 {@code t_account} 存在 + 状态正常；
 *       使用 {@code last_active_identity_*} 作为默认身份签发 JWT；
 *       前端可在拿到 token 后调用 {@code switch-identity} 切换其他身份（M2 引入）。</li>
 *   <li>{@link #switchTenant} —— 仅 C 端业主（identityType=C_USER）可用；校验 {@code uid}
 *       在目标小区是否拥有房产绑定关系，重新签发 token。</li>
 * </ol>
 *
 * <p>JWT 不嵌 roles / permissions：每次请求由 {@code JwtAuthenticationFilter} → {@code UserContextLoader}
 * 实时反查（M2 引入 Redis 5 min TTL 缓存）。
 */
@Service
public class AuthService {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private IdentityShadowMapper identityShadowMapper;

    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Autowired
    private UserContextLoader userContextLoader;

    @Autowired
    private PropertyGateway propertyGateway;

    @Autowired
    private SmsVerificationStrategy smsVerificationStrategy;

    /**
     * 双端统一登录：手机号 + 短信验证码 → 默认身份 → JWT。
     *
     * <p>失败语义：
     * <ul>
     *   <li>账号未注册（前置短信发送时其实已经过滤；这里再次兜底） → {@code USER_NOT_REGISTERED}；</li>
     *   <li>账号被禁用 / 注销 → {@code UNAUTHORIZED}；</li>
     *   <li>没有任何已绑定身份（既无 sys_user 也无 c_user） → {@code USER_NOT_REGISTERED}。</li>
     * </ul>
     */
    public Map<String, Object> login(LoginRequest request) {
        AccountMapper.AccountRow account = accountMapper.selectByPhone(request.getUsername());
        if (account == null) {
            throw new AppException(CommonErrorCode.USER_NOT_REGISTERED);
        }
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "账号已被禁用或注销，请联系管理员");
        }

        // 短信验证码校验（生产由 RealSmsStrategy；本地由 MockSmsStrategy 放行 666666）
        smsVerificationStrategy.validate(request.getUsername(), request.getSmsCode());

        // 选择默认身份：last_active_identity_* 优先；为空时不允许登录（应由 OnboardingService 实名落卡后回填，M1 不实现）
        UserContext.IdentityType defaultType = resolveDefaultIdentityType(account);
        Long defaultIdentityId = account.getLastActiveIdentityId();
        if (defaultIdentityId == null) {
            throw new AppException(CommonErrorCode.USER_NOT_REGISTERED,
                    "账号尚未绑定可用身份，请前往居委会完成实名登记");
        }

        // 用 UserContextLoader 加载默认身份；这一步同时校验该身份在 DB 中确实存在并启用
        UserContext ctx = userContextLoader.load(account.getAccountId(), defaultType, defaultIdentityId, null);
        if (ctx == null) {
            throw new AppException(CommonErrorCode.USER_NOT_REGISTERED,
                    "账号无可用身份，请前往居委会完成实名登记");
        }

        // 回填 last_active_identity（首次登录的账户使用）
        accountMapper.updateLastActiveIdentity(account.getAccountId(),
                ctx.activeIdentityId(), ctx.identityType().name());

        String token = jwtTokenProvider.generateToken(
                ctx.accountId(), ctx.identityType().name(), ctx.activeIdentityId(), ctx.tenantId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", token);
        result.put("expires_in", 7200);
        result.put("user_info", buildUserInfo(ctx));
        return result;
    }

    /**
     * 查询当前自然人名下的管理端工作分身列表。
     */
    public Map<String, Object> listSysUserShadows(String authHeader) {
        String token = extractValidToken(authHeader);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String identityType = jwtTokenProvider.getIdentityTypeFromToken(token);
        Long activeIdentityId = jwtTokenProvider.getActiveIdentityIdFromToken(token);

        List<Map<String, Object>> shadows = identityShadowMapper.listSysUserShadows(accountId).stream()
                .map(row -> buildShadowInfo(row, UserContext.IdentityType.SYS_USER.name().equals(identityType)
                        && row.getUserId().equals(activeIdentityId)))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shadows", shadows);
        return result;
    }

    /**
     * 当前身份可见管理端导航菜单。
     *
     * <p>菜单从后端 {@code sys_menu + sys_role_menu} 下发，按 {@code order_num}
     * 排序。接口鉴权仍由对应业务 endpoint 的 {@code @PreAuthorize} 最终兜底。
     */
    public List<NavMenuResponse> listMenus(String authHeader) {
        UserContext ctx = loadContextFromToken(authHeader);
        List<SysMenuMapper.SysMenuRow> rows = ctx.isSysUser()
                ? sysMenuMapper.selectGrantedMenusByUserId(ctx.userId())
                : List.of();
        return toMenuTree(rows);
    }

    private List<NavMenuResponse> toMenuTree(List<SysMenuMapper.SysMenuRow> rows) {
        Map<Long, List<SysMenuMapper.SysMenuRow>> childrenByParent = rows.stream()
                .filter(row -> row.getParentId() != null && row.getParentId() != 0L)
                .collect(Collectors.groupingBy(SysMenuMapper.SysMenuRow::getParentId, LinkedHashMap::new, Collectors.toList()));

        return rows.stream()
                .filter(row -> row.getParentId() != null && row.getParentId() == 0L)
                .map(parent -> {
                    List<NavPageResponse> pages = childrenByParent.getOrDefault(parent.getMenuId(), List.of()).stream()
                            .map(child -> new NavPageResponse(child.getRouteId(), child.getMenuName(), child.getOrderNum()))
                            .toList();
                    if (pages.isEmpty()) {
                        return null;
                    }
                    return new NavMenuResponse(
                            parent.getRouteId(),
                            parent.getMenuName(),
                            parent.getIcon(),
                            parent.getOrderNum(),
                            pages);
                })
                .filter(item -> item != null)
                .toList();
    }

    /**
     * 管理端工作分身切换：同一 {@code account_id} 名下的 {@code sys_user} 之间切换，并重发 JWT。
     */
    public Map<String, Object> switchShadow(String authHeader, SwitchShadowRequest request) {
        String token = extractValidToken(authHeader);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        Long targetUserId = request == null ? null : request.getTargetUserId();
        if (targetUserId == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "targetUserId 不能为空");
        }

        AccountMapper.AccountRow account = accountMapper.selectById(accountId);
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "账号已被禁用或注销，请联系管理员");
        }

        IdentityShadowMapper.SysUserShadowRow shadow =
                identityShadowMapper.selectSysUserShadow(accountId, targetUserId);
        if (shadow == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "目标工作分身不存在或不属于当前账号");
        }

        UserContext ctx = userContextLoader.load(accountId, UserContext.IdentityType.SYS_USER, targetUserId, null);
        if (ctx == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "目标工作分身不可用");
        }
        accountMapper.updateLastActiveIdentity(accountId, ctx.activeIdentityId(), ctx.identityType().name());

        String newToken = jwtTokenProvider.generateToken(
                ctx.accountId(), ctx.identityType().name(), ctx.activeIdentityId(), ctx.tenantId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("new_access_token", newToken);
        result.put("expires_in", 7200);
        result.put("user_info", buildUserInfo(ctx));
        result.put("active_shadow", buildShadowInfo(shadow, true));
        return result;
    }

    /**
     * C 端业主跨小区切换：仅当 token 内 {@code identityType=C_USER} 才允许。
     * 校验目标 tenant 下确有房产绑定，重新签发 token。
     */
    public Map<String, Object> switchTenant(String authHeader, SwitchTenantRequest request) {
        String token = extractValidToken(authHeader);

        String identityType = jwtTokenProvider.getIdentityTypeFromToken(token);
        if (!UserContext.IdentityType.C_USER.name().equals(identityType)) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "仅 C 端业主身份可切换小区");
        }

        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        Long uid = jwtTokenProvider.getActiveIdentityIdFromToken(token);
        Long targetTenantId = request.getTargetTenantId();

        // 真实性校验：当前 uid 在目标小区是否有房产绑定
        List<PropertyOwnership> ownerships = propertyGateway.getOwnerships(uid, targetTenantId);
        if (ownerships == null || ownerships.isEmpty()) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED_TENANT);
        }

        // 用新 tenantId 重新装配 UserContext + 签发 token
        UserContext ctx = userContextLoader.load(accountId, UserContext.IdentityType.C_USER, uid, targetTenantId);
        String newToken = jwtTokenProvider.generateToken(
                ctx.accountId(), ctx.identityType().name(), ctx.activeIdentityId(), ctx.tenantId());

        List<Long> activeOpidList = ownerships.stream()
                .map(PropertyOwnership::getOpid)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("new_access_token", newToken);
        result.put("active_tenant_id", ctx.tenantId());
        result.put("active_opid_list", activeOpidList);
        return result;
    }

    /**
     * 选择默认身份类型。{@code last_active_identity_type} 为空时返回 {@code SYS_USER} 作为默认（多数账号都先发管理端身份）。
     * UserContextLoader 内部会再做一次 SYS_USER → C_USER 兜底。
     */
    private UserContext.IdentityType resolveDefaultIdentityType(AccountMapper.AccountRow account) {
        String tag = account.getLastActiveIdentityType();
        if (UserContext.IdentityType.C_USER.name().equals(tag)) {
            return UserContext.IdentityType.C_USER;
        }
        return UserContext.IdentityType.SYS_USER;
    }

    private String extractValidToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AppException(CommonErrorCode.TOKEN_MISSING);
        }
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "认证失效，请重新登录");
        }
        return token;
    }

    private UserContext loadContextFromToken(String authHeader) {
        String token = extractValidToken(authHeader);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String identityType = jwtTokenProvider.getIdentityTypeFromToken(token);
        Long activeIdentityId = jwtTokenProvider.getActiveIdentityIdFromToken(token);
        Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
        return userContextLoader.load(
                accountId,
                UserContext.IdentityType.valueOf(identityType),
                activeIdentityId,
                tenantId);
    }

    private Map<String, Object> buildUserInfo(UserContext ctx) {
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("account_id", ctx.accountId());
        userInfo.put("identity_type", ctx.identityType().name());
        userInfo.put("active_identity_id", ctx.activeIdentityId());
        userInfo.put("tenant_id", ctx.tenantId());
        userInfo.put("dept_type", ctx.deptType());
        userInfo.put("auth_level", ctx.authLevel().getValue());
        userInfo.put("role_key", ctx.roleKey());
        userInfo.put("permissions", ctx.permissions());
        userInfo.put("menu_permissions", menuRouteIds(ctx));
        return userInfo;
    }

    private List<String> menuRouteIds(UserContext ctx) {
        if (!ctx.isSysUser()) {
            return List.of();
        }
        return sysMenuMapper.selectGrantedMenusByUserId(ctx.userId()).stream()
                .filter(row -> row.getParentId() != null && row.getParentId() != 0L)
                .map(SysMenuMapper.SysMenuRow::getRouteId)
                .distinct()
                .toList();
    }

    private Map<String, Object> buildShadowInfo(IdentityShadowMapper.SysUserShadowRow row, boolean active) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("user_id", row.getUserId());
        info.put("dept_id", row.getDeptId());
        info.put("tenant_id", row.getTenantId());
        info.put("user_name", row.getUserName());
        info.put("nick_name", row.getNickName());
        info.put("dept_type", row.getDeptType());
        info.put("dept_category", row.getDeptCategory());
        info.put("dept_name", row.getDeptName());
        info.put("role_id", row.getRoleId());
        info.put("role_key", row.getRoleKey());
        info.put("role_name", row.getRoleName());
        info.put("effective_data_scope", row.getEffectiveDataScope());
        info.put("active", active);
        return info;
    }
}
