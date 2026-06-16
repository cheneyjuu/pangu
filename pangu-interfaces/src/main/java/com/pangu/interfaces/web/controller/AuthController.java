package com.pangu.interfaces.web.controller;

import com.pangu.domain.gateway.UserGateway;
import com.pangu.domain.gateway.PropertyGateway;
import com.pangu.domain.model.user.NaturalPerson;
import com.pangu.domain.model.asset.PropertyOwnership;
import com.pangu.interfaces.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 双端统一安全认证与多租户会话控制器
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends BaseController {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserGateway userGateway;

    @Autowired
    private PropertyGateway propertyGateway;

    /**
     * 1. 双端统一登录认证接口 (通过手机号从数据库加载用户数据)
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        // 从 Postgres 数据库中检索该手机号注册的自然人用户
        NaturalPerson person = userGateway.getByPhone(request.getUsername());

        if (person == null) {
            throw new AppException(401, "认证失败：该手机号未注册，请前往居委会完成线下实名核验登记");
        }

        // 获取该用户的默认小区 ID (取其名下第一套绑定房产的小区 ID，若无则默认为 9001)
        Long defaultTenantId = 9001L;
        List<PropertyOwnership> ownerships = propertyGateway.getOwnerships(person.getUid(), defaultTenantId);
        
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

        Map<String, Object> data = Map.of(
                "access_token", token,
                "expires_in", 7200,
                "user_info", Map.of(
                        "uid", person.getUid(),
                        "auth_level", person.getAuthLevel().getValue(),
                        "current_tenant_id", defaultTenantId
                )
        );

        return success(data);
    }

    /**
     * 2. C端业主跨小区多租户切换接口 (基于数据库房产数据动态切换)
     */
    @PostMapping("/switch-tenant")
    public Result<Map<String, Object>> switchTenant(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SwitchTenantRequest request) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AppException(401, "无访问权限：请携带 Token");
        }

        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new AppException(401, "认证失效，请重新登录");
        }

        Long uid = jwtTokenProvider.getUidFromToken(token);
        Long targetTenantId = request.getTargetTenantId();

        // 真实性验证：从数据库查询当前用户在目标小区是否确有房产所有权绑定记录
        List<PropertyOwnership> targetOwnerships = propertyGateway.getOwnerships(uid, targetTenantId);
        if (targetOwnerships == null || targetOwnerships.isEmpty()) {
            throw new AppException(403, "越权访问：您在目标小区名下没有绑定的房产，拒绝切换");
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

        Map<String, Object> data = Map.of(
                "new_access_token", newToken,
                "active_opid_list", activeOpidList
        );

        return success("切换成功", data);
    }

    // ===================================================================
    // 请求 DTO 定义
    // ===================================================================

    public static class LoginRequest {
        private String username;
        private String smsCode;
        private Integer loginType;
        private String clientPortal;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getSmsCode() { return smsCode; }
        public void setSmsCode(String smsCode) { this.smsCode = smsCode; }
        public Integer getLoginType() { return loginType; }
        public void setLoginType(Integer loginType) { this.loginType = loginType; }
        public String getClientPortal() { return clientPortal; }
        public void setClientPortal(String clientPortal) { this.clientPortal = clientPortal; }
    }

    public static class SwitchTenantRequest {
        private Long targetTenantId;

        public Long getTargetTenantId() { return targetTenantId; }
        public void setTargetTenantId(Long targetTenantId) { this.targetTenantId = targetTenantId; }
    }
}
