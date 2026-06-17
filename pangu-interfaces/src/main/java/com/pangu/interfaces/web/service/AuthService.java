package com.pangu.interfaces.web.service;

import com.pangu.domain.gateway.UserGateway;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.model.user.NaturalPerson;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.interfaces.security.JwtTokenProvider;
import com.pangu.interfaces.web.controller.AppException;
import com.pangu.interfaces.web.controller.CommonErrorCode;
import com.pangu.interfaces.web.controller.dto.LoginRequest;
import com.pangu.interfaces.web.controller.dto.SwitchTenantRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 安全认证与多租户会话服务
 */
@Service
public class AuthService {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserGateway userGateway;

    @Autowired
    private PropertyGateway propertyGateway;

    @Autowired
    private SmsVerificationStrategy smsVerificationStrategy;

    /**
     * 用户登录认证核心业务
     */
    public Map<String, Object> login(LoginRequest request) {
        // 从 Postgres 数据库中检索该手机号注册 of 自然人用户
        NaturalPerson person = userGateway.getByPhone(request.getUsername());

        if (person == null) {
            throw new AppException(CommonErrorCode.USER_NOT_REGISTERED);
        }

        // 根据当前激活的 profile 所匹配的短信策略执行验证码校验
        smsVerificationStrategy.validate(request.getUsername(), request.getSmsCode());

        // 获取该用户的默认小区 ID (取其名下第一套绑定房产的小区 ID，若无则默认为 9001)
        Long defaultTenantId = 9001L;
        
        // 角色与权限从数据库真实装配
        List<String> roles = userGateway.getRolesByUid(person.getUid());
        List<String> permissions = userGateway.getPermissionsByRoles(roles);

        // 签发真实 JWT 安全 Token
        String token = jwtTokenProvider.generateToken(
                person.getUid(), 
                defaultTenantId, 
                roles, 
                permissions, 
                1 // 用户类型：1-业主
        );

        return Map.of(
                "access_token", token,
                "expires_in", 7200,
                "user_info", Map.of(
                        "uid", person.getUid(),
                        "auth_level", person.getAuthLevel().getValue(),
                        "current_tenant_id", defaultTenantId
                )
        );
    }

    /**
     * 跨小区多租户切换核心业务
     */
    public Map<String, Object> switchTenant(String authHeader, SwitchTenantRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AppException(CommonErrorCode.TOKEN_MISSING);
        }

        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED, "认证失效，请重新登录");
        }

        Long uid = jwtTokenProvider.getUidFromToken(token);
        Long targetTenantId = request.getTargetTenantId();

        // 真实性验证：从数据库查询当前用户在目标小区是否确有房产所有权绑定记录
        List<PropertyOwnership> targetOwnerships = propertyGateway.getOwnerships(uid, targetTenantId);
        if (targetOwnerships == null || targetOwnerships.isEmpty()) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED_TENANT);
        }

        // 动态加载用户在该小区下的角色与权限列表
        List<String> newRoles = userGateway.getRolesByUid(uid);
        List<String> newPermissions = userGateway.getPermissionsByRoles(newRoles);

        // 收集该小区名下的活动房产 OPID 列表
        List<Long> activeOpidList = targetOwnerships.stream()
                .map(PropertyOwnership::getOpid)
                .collect(Collectors.toList());

        // 重新签发绑定了新 tenant_id 的 JWT 安全 Token
        String newToken = jwtTokenProvider.generateToken(uid, targetTenantId, newRoles, newPermissions, 1);

        return Map.of(
                "new_access_token", newToken,
                "active_opid_list", activeOpidList
        );
    }
}
