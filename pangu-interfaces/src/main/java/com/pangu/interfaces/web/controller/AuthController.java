package com.pangu.interfaces.web.controller;

import com.pangu.interfaces.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 双端统一安全认证与多租户会话控制器
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * 1. 双端统一登录认证接口 (Mock 校验数据库播种用户)
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        // Mock 数据库播种用户的简单比对认证
        Long uid = null;
        Integer authLevel = 1;
        Long defaultTenantId = 9001L;
        List<String> roles = new ArrayList<>();
        List<String> permissions = new ArrayList<>();

        if ("13800138000".equals(request.getUsername())) {
            uid = 101L; // 张三 (L3 级业主，兼 G端网格员)
            authLevel = 3;
            roles = List.of("grid_manager");
            permissions = List.of("repair:view", "election:vote");
        } else if ("13900139000".equals(request.getUsername())) {
            uid = 102L; // 李四 (L1 级业主)
            authLevel = 1;
            permissions = List.of("election:vote");
        } else if ("15000150000".equals(request.getUsername())) {
            uid = 103L; // 王五 (L4 级法人业主)
            authLevel = 4;
            permissions = List.of("election:vote");
        } else {
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "msg", "认证失败：未注册手机号或验证码错误"
            ));
        }

        // 签发 JWT
        String token = jwtTokenProvider.generateToken(uid, defaultTenantId, roles, permissions, 1);

        Map<String, Object> data = Map.of(
                "access_token", token,
                "expires_in", 7200,
                "user_info", Map.of(
                        "uid", uid,
                        "auth_level", authLevel,
                        "current_tenant_id", defaultTenantId
                )
        );

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "msg", "success",
                "data", data
        ));
    }

    /**
     * 2. C端业主跨小区多租户切换接口
     */
    @PostMapping("/switch-tenant")
    public ResponseEntity<Map<String, Object>> switchTenant(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SwitchTenantRequest request) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "msg", "无访问权限：请携带 Token"));
        }

        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "msg", "认证失效，请重新登录"));
        }

        Long uid = jwtTokenProvider.getUidFromToken(token);
        Long targetTenantId = request.getTargetTenantId();

        // Mock 业主房产租户匹配验证：
        // 现实中会在 Domain/App 层的 c_owner_property 表进行 UID 与 tenant_id 的绑定验证
        // 这里进行 mock 校验：允许 101/102/103 访问小区 9001 或 9002
        if (targetTenantId != 9001L && targetTenantId != 9002L) {
            return ResponseEntity.status(403).body(Map.of(
                    "code", 403,
                    "msg", "越权访问：您在目标小区名下没有绑定的房产，拒绝切换"
            ));
        }

        // 获取该自然人用户在目标小区下的角色和权限配置 (此处进行 mock 装配)
        List<String> newRoles = new ArrayList<>();
        List<String> newPermissions = List.of("election:vote");
        List<Long> activeOpidList = new ArrayList<>();

        if (uid == 101L) {
            if (targetTenantId == 9001L) {
                newRoles = List.of("grid_manager");
                newPermissions = List.of("repair:view", "election:vote");
                activeOpidList = List.of(5001L);
            } else {
                activeOpidList = List.of(5004L); // 假设在 9002 小区下的 OPID
            }
        } else if (uid == 102L) {
            activeOpidList = List.of(5002L);
        } else if (uid == 103L) {
            activeOpidList = List.of(5003L);
        }

        // 重新签发新租户小区的增强型安全 Token
        String newToken = jwtTokenProvider.generateToken(uid, targetTenantId, newRoles, newPermissions, 1);

        Map<String, Object> data = Map.of(
                "new_access_token", newToken,
                "active_opid_list", activeOpidList
        );

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "msg", "切换成功",
                "data", data
        ));
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
