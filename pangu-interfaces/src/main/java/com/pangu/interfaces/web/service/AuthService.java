// 关联业务：统一登录、实名认证与会话资料下发，确保 C 端展示当前登录人的真实会话信息。
package com.pangu.interfaces.web.service;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextLoader;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.gateway.identity.IdCardOcrGateway;
import com.pangu.domain.model.identity.ChineseResidentId;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.domain.model.community.GovernmentManagedCommunity;
import com.pangu.domain.model.user.WorkIdentityShadow;
import com.pangu.domain.repository.AuthAccountRepository;
import com.pangu.domain.repository.GovernmentManagedCommunityRepository;
import com.pangu.domain.repository.IdentityShadowRepository;
import com.pangu.domain.repository.NavigationMenuRepository;
import com.pangu.domain.repository.OwnerIdentityVerificationRepository;
import com.pangu.domain.security.NameDecryptor;
import com.pangu.interfaces.security.JwtTokenProvider;
import com.pangu.interfaces.web.controller.dto.LoginRequest;
import com.pangu.interfaces.web.controller.dto.NavMenuResponse;
import com.pangu.interfaces.web.controller.dto.NavPageResponse;
import com.pangu.interfaces.web.controller.dto.SwitchShadowRequest;
import com.pangu.interfaces.web.controller.dto.SwitchTenantRequest;
import com.pangu.interfaces.web.controller.dto.owner.IdCardOcrRequest;
import com.pangu.interfaces.web.controller.dto.owner.IdCardOcrResponse;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 双端统一登录与多租户会话服务（M1 RBAC 重构后版本）。
 *
 * <p>登录流程：
 * <ol>
     *   <li>{@link #login} —— 手机号 + 短信验证码 → 校验 {@code t_account} 状态；
     *       C 端冷启动新手机号自动落自然人账号与 {@code c_user}；使用默认身份签发 JWT；
 *       前端可在拿到 token 后调用 {@code switch-identity} 切换其他身份（M2 引入）。</li>
 *   <li>{@link #switchTenant} —— 仅 C 端业主（identityType=C_USER）可用；校验 {@code uid}
 *       在目标小区是否拥有房产绑定关系，重新签发 token。</li>
 *   <li>{@link #switchManagedCommunity} —— G 端根组织仅能在后端确认的辖区小区之间切换，
 *       重新签发带目标 tenant 上下文的 token。</li>
 * </ol>
 *
 * <p>JWT 不嵌 roles / permissions：每次请求由 {@code JwtAuthenticationFilter} → {@code UserContextLoader}
 * 实时反查（M2 引入 Redis 5 min TTL 缓存）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthAccountRepository authAccountRepository;
    private final IdentityShadowRepository identityShadowRepository;
    private final NavigationMenuRepository navigationMenuRepository;
    private final GovernmentManagedCommunityRepository governmentManagedCommunityRepository;
    private final UserContextLoader userContextLoader;
    private final PropertyGateway propertyGateway;
    private final SmsVerificationStrategy smsVerificationStrategy;
    private final OwnerIdentityVerificationRepository ownerIdentityVerificationRepository;
    private final IdCardOcrGateway idCardOcrGateway;
    private final NameDecryptor nameDecryptor;

    /**
     * 双端统一登录：手机号 + 短信验证码 → 默认身份 → JWT。
     *
     * <p>失败语义：
     * <ul>
     *   <li>管理端账号未注册 → {@code USER_NOT_REGISTERED}；C 端冷启动新手机号自动注册；</li>
     *   <li>账号被禁用 / 注销 → {@code UNAUTHORIZED}；</li>
     *   <li>没有任何已绑定身份（既无 sys_user 也无 c_user） → {@code USER_NOT_REGISTERED}。</li>
     * </ul>
     */
    @Transactional
    public Map<String, Object> login(LoginRequest request) {
        String phone = normalizePhone(request.getUsername());
        if (phone == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "手机号不能为空");
        }
        // 短信验证码校验（生产由 RealSmsStrategy；本地由 MockSmsStrategy 放行 123456）
        smsVerificationStrategy.validate(phone, request.getSmsCode());

        AuthAccountRepository.AccountSnapshot account = authAccountRepository.findByPhone(phone);
        if (account == null) {
            if (!isColdStartOwnerPortal(request.getClientPortal())) {
                throw new AppException(CommonErrorCode.USER_NOT_REGISTERED);
            }
            account = createColdStartOwnerAccount(phone);
        }
        if (account.status() == null || account.status() != 1) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "账号已被禁用或注销，请联系管理员");
        }

        // 选择默认身份：last_active_identity_* 优先；为空时允许 C 端已有 c_user 身份兜底。
        UserContext.IdentityType defaultType = resolveDefaultIdentityType(account);
        Long defaultIdentityId = account.lastActiveIdentityId();
        if (defaultIdentityId == null) {
            Long uid = authAccountRepository.findCUserUidByAccountId(account.accountId());
            if (uid == null) {
                throw new AppException(CommonErrorCode.USER_NOT_REGISTERED,
                        "账号尚未绑定可用身份，请前往居委会完成实名登记");
            }
            defaultType = UserContext.IdentityType.C_USER;
            defaultIdentityId = uid;
        }

        // 用 UserContextLoader 加载默认身份；这一步同时校验该身份在 DB 中确实存在并启用
        UserContext ctx = userContextLoader.load(account.accountId(), defaultType, defaultIdentityId, null);
        if (ctx == null) {
            throw new AppException(CommonErrorCode.USER_NOT_REGISTERED,
                    "账号无可用身份，请前往居委会完成实名登记");
        }

        // 回填 last_active_identity（首次登录的账户使用）
        authAccountRepository.updateLastActiveIdentity(account.accountId(),
                ctx.activeIdentityId(), ctx.identityType().name());

        String token = jwtTokenProvider.generateToken(
                ctx.accountId(), ctx.identityType().name(), ctx.activeIdentityId(), ctx.tenantId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", token);
        result.put("expires_in", 7200);
        result.put("user_info", buildUserInfo(ctx));
        return result;
    }

    public IdCardOcrResponse recognizeIdCard(Long accountId, Long uid, IdCardOcrRequest request) {
        if (accountId == null || uid == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "未识别到业主身份，禁止身份证 OCR");
        }
        if (request == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "身份证图片不能为空");
        }
        String imageBase64 = stripDataUrlPrefix(trimToNull(request.imageBase64()));
        String imageUrl = trimToNull(request.imageUrl());
        if (imageBase64 == null && imageUrl == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "请上传身份证人像面图片");
        }
        if (imageBase64 != null && imageUrl != null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "imageBase64 与 imageUrl 不能同时提交");
        }
        String cardSide = trimToNull(request.cardSide());
        if (cardSide != null && !IdCardOcrGateway.CARD_SIDE_FRONT.equalsIgnoreCase(cardSide)) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "L2 认证仅支持识别身份证人像面");
        }

        IdCardOcrGateway.OcrResult result;
        try {
            result = idCardOcrGateway.recognize(new IdCardOcrGateway.OcrRequest(
                    imageBase64, imageUrl, IdCardOcrGateway.CARD_SIDE_FRONT));
        } catch (IllegalArgumentException e) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new AppException(CommonErrorCode.SYSTEM_ERROR, "身份证 OCR 服务暂不可用", e);
        }

        String normalizedId = ChineseResidentId.normalize(result.idCardNumber());
        boolean recognized = result.recognized()
                && trimToNull(result.realName()) != null
                && ChineseResidentId.isValid(normalizedId);
        String reason = recognized ? result.reason() : firstText(result.reason(), "身份证 OCR 未识别出有效姓名或证件号码");
        return new IdCardOcrResponse(
                recognized,
                result.provider(),
                recognized ? trimToNull(result.realName()) : null,
                recognized ? normalizedId : null,
                recognized ? ChineseResidentId.mask(normalizedId) : null,
                result.requestId(),
                result.qualityScore(),
                result.warnings(),
                reason);
    }

    @Transactional
    public Map<String, Object> verifyRealName(Long accountId, Long uid, String realName, String idCardNumber) {
        String normalizedName = trimToNull(realName);
        String normalizedId = ChineseResidentId.normalize(idCardNumber);
        if (accountId == null || uid == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "未识别到业主身份，禁止实名认证");
        }
        if (normalizedName == null || normalizedName.length() < 2) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "真实姓名不能为空且至少 2 个字");
        }
        if (ChineseResidentId.isPlaceholder(normalizedName, normalizedId)) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "测试或占位身份不能通过实名认证");
        }
        if (!ChineseResidentId.isValid(normalizedId)) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "身份证号格式或校验码不正确");
        }
        authAccountRepository.updateIdentity(accountId, normalizedName, normalizedId);
        ownerIdentityVerificationRepository.upgradeCUserAuthLevel(uid, accountId, 2);
        authAccountRepository.updateLastActiveIdentity(accountId, uid, UserContext.IdentityType.C_USER.name());
        UserContext ctx = userContextLoader.load(accountId, UserContext.IdentityType.C_USER, uid, null);
        if (ctx == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "业主身份不可用");
        }
        Map<String, Object> result = new LinkedHashMap<>();
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

        List<Map<String, Object>> shadows = identityShadowRepository.listSysUserShadows(accountId).stream()
                .map(row -> buildShadowInfo(row, UserContext.IdentityType.SYS_USER.name().equals(identityType)
                        && row.userId().equals(activeIdentityId)))
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
        List<NavigationMenuRepository.MenuItem> rows = ctx.isSysUser()
                ? navigationMenuRepository.findGrantedMenusByUserId(ctx.userId())
                : List.of();
        return toMenuTree(rows);
    }

    private List<NavMenuResponse> toMenuTree(List<NavigationMenuRepository.MenuItem> rows) {
        Map<Long, List<NavigationMenuRepository.MenuItem>> childrenByParent = rows.stream()
                .filter(row -> row.parentId() != null && row.parentId() != 0L)
                .collect(Collectors.groupingBy(NavigationMenuRepository.MenuItem::parentId,
                        LinkedHashMap::new, Collectors.toList()));

        return rows.stream()
                .filter(row -> row.parentId() != null && row.parentId() == 0L)
                .map(parent -> {
                    List<NavPageResponse> pages = childrenByParent.getOrDefault(parent.menuId(), List.of()).stream()
                            .map(child -> new NavPageResponse(child.routeId(), child.menuName(), child.orderNum()))
                            .toList();
                    if (pages.isEmpty()) {
                        return null;
                    }
                    return new NavMenuResponse(
                            parent.routeId(),
                            parent.menuName(),
                            parent.icon(),
                            parent.orderNum(),
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

        AuthAccountRepository.AccountSnapshot account = authAccountRepository.findById(accountId);
        if (account == null || account.status() == null || account.status() != 1) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "账号已被禁用或注销，请联系管理员");
        }

        WorkIdentityShadow shadow =
                identityShadowRepository.findSysUserShadow(accountId, targetUserId);
        if (shadow == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "目标工作分身不存在或不属于当前账号");
        }

        UserContext ctx = userContextLoader.load(accountId, UserContext.IdentityType.SYS_USER, targetUserId, null);
        if (ctx == null) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "目标工作分身不可用");
        }
        authAccountRepository.updateLastActiveIdentity(accountId, ctx.activeIdentityId(), ctx.identityType().name());

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
     * 返回当前街镇或平台根组织可监管的小区列表。
     *
     * <p>列表由组织树和 {@code sys_dept_tenant_scope} 共同决定，不能由前端静态配置或
     * 请求参数替代。
     */
    public Map<String, Object> listManagedCommunities(String authHeader) {
        UserContext ctx = requireGovernmentCommunitySwitcher(authHeader);
        List<Map<String, Object>> communities = governmentManagedCommunityRepository
                .listManagedCommunities(ctx.deptId())
                .stream()
                .map(this::buildManagedCommunityInfo)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active_tenant_id", ctx.tenantId());
        result.put("communities", communities);
        return result;
    }

    /**
     * G 端辖区小区上下文切换。
     *
     * <p>不复用 C 端 {@link #switchTenant}：两种切换的合法性来源不同。业主依据房产绑定，
     * 政府组织依据组织树和有效监管范围。
     */
    public Map<String, Object> switchManagedCommunity(String authHeader, SwitchTenantRequest request) {
        UserContext current = requireGovernmentCommunitySwitcher(authHeader);
        Long targetTenantId = request == null ? null : request.getTargetTenantId();
        if (targetTenantId == null) {
            throw new AppException(CommonErrorCode.PARAM_ERROR, "targetTenantId 不能为空");
        }
        if (!governmentManagedCommunityRepository.canManageCommunity(current.deptId(), targetTenantId)) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "目标小区不在当前监管范围内");
        }

        UserContext target = userContextLoader.load(
                current.accountId(), UserContext.IdentityType.SYS_USER,
                current.activeIdentityId(), targetTenantId);
        if (target == null || !Objects.equals(targetTenantId, target.tenantId())) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "目标小区会话上下文不可用");
        }

        String newToken = jwtTokenProvider.generateToken(
                target.accountId(), target.identityType().name(), target.activeIdentityId(), target.tenantId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("new_access_token", newToken);
        result.put("expires_in", 7200);
        result.put("active_tenant_id", target.tenantId());
        result.put("user_info", buildUserInfo(target));
        return result;
    }

    /**
     * 选择默认身份类型。{@code last_active_identity_type} 为空时返回 {@code SYS_USER} 作为默认（多数账号都先发管理端身份）。
     * UserContextLoader 内部会再做一次 SYS_USER → C_USER 兜底。
     */
    private UserContext.IdentityType resolveDefaultIdentityType(AuthAccountRepository.AccountSnapshot account) {
        String tag = account.lastActiveIdentityType();
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

    private UserContext requireGovernmentCommunitySwitcher(String authHeader) {
        UserContext ctx = loadContextFromToken(authHeader);
        boolean eligible = ctx != null
                && ctx.isSysUser()
                && ctx.deptCategory() == UserContext.DeptCategory.G
                && Integer.valueOf(1).equals(ctx.deptType())
                && ctx.deptId() != null;
        if (!eligible) {
            throw new AppException(CommonErrorCode.FORBIDDEN, "当前工作身份无权切换辖区小区");
        }
        return ctx;
    }

    private Map<String, Object> buildManagedCommunityInfo(GovernmentManagedCommunity community) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("tenant_id", community.tenantId());
        info.put("tenant_name", community.tenantName());
        info.put("planned_household_count", community.plannedHouseholdCount());
        info.put("total_exclusive_area", community.totalExclusiveArea());
        info.put("governance_status", community.governanceStatus());
        return info;
    }

    private Map<String, Object> buildUserInfo(UserContext ctx) {
        Map<String, Object> userInfo = new LinkedHashMap<>();
        AuthAccountRepository.AccountIdentitySnapshot identity =
                authAccountRepository.findIdentityByAccountId(ctx.accountId());
        userInfo.put("account_id", ctx.accountId());
        userInfo.put("identity_type", ctx.identityType().name());
        userInfo.put("active_identity_id", ctx.activeIdentityId());
        userInfo.put("tenant_id", ctx.tenantId());
        userInfo.put("dept_type", ctx.deptType());
        userInfo.put("auth_level", ctx.authLevel().getValue());
        userInfo.put("role_key", ctx.roleKey());
        userInfo.put("permissions", ctx.permissions());
        userInfo.put("menu_permissions", menuRouteIds(ctx));
        // 仅回传当前会话本人已完成实名核验的姓名；未实名账户不能从产权名册反向补造身份。
        userInfo.put("display_name", identity != null && Integer.valueOf(1).equals(identity.realNameVerified())
                ? trimToNull(nameDecryptor.safeDecrypt(identity.realNameCipher()))
                : null);
        userInfo.put("phone", identity == null ? null : identity.phone());
        return userInfo;
    }

    private List<String> menuRouteIds(UserContext ctx) {
        if (!ctx.isSysUser()) {
            return List.of();
        }
        return navigationMenuRepository.findGrantedMenusByUserId(ctx.userId()).stream()
                .filter(row -> row.parentId() != null && row.parentId() != 0L)
                .map(NavigationMenuRepository.MenuItem::routeId)
                .distinct()
                .toList();
    }

    private Map<String, Object> buildShadowInfo(WorkIdentityShadow row, boolean active) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("user_id", row.userId());
        info.put("dept_id", row.deptId());
        info.put("tenant_id", row.tenantId());
        info.put("user_name", row.userName());
        info.put("nick_name", row.nickName());
        info.put("dept_type", row.deptType());
        info.put("dept_category", row.deptCategory());
        info.put("dept_name", row.deptName());
        info.put("role_id", row.roleId());
        info.put("role_key", row.roleKey());
        info.put("role_name", row.roleName());
        info.put("effective_data_scope", row.effectiveDataScope());
        info.put("active", active);
        return info;
    }

    private AuthAccountRepository.AccountSnapshot createColdStartOwnerAccount(String phone) {
        return authAccountRepository.createColdStartOwnerAccount(phone);
    }

    private boolean isColdStartOwnerPortal(String clientPortal) {
        String portal = clientPortal == null ? "" : clientPortal.trim().toUpperCase();
        return "AUTO".equals(portal) || "C".equals(portal) || "OWNER".equals(portal);
    }

    private String stripDataUrlPrefix(String imageBase64) {
        if (imageBase64 == null) {
            return null;
        }
        int commaIndex = imageBase64.indexOf(',');
        if (imageBase64.startsWith("data:") && commaIndex > 0) {
            return trimToNull(imageBase64.substring(commaIndex + 1));
        }
        return imageBase64;
    }

    private String firstText(String first, String fallback) {
        return trimToNull(first) != null ? trimToNull(first) : fallback;
    }

    private String normalizePhone(String phone) {
        String value = trimToNull(phone);
        return value == null ? null : value.replaceAll("\\s+", "");
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
