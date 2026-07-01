package com.pangu.interfaces.web.controller;

import com.pangu.interfaces.web.controller.dto.LoginRequest;
import com.pangu.interfaces.web.controller.dto.SwitchShadowRequest;
import com.pangu.interfaces.web.controller.dto.SwitchTenantRequest;
import com.pangu.interfaces.web.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 双端统一安全认证与多租户会话控制器
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends BaseController {

    @Autowired
    private AuthService authService;

    /**
     * 1. 双端统一登录认证接口
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return success(authService.login(request));
    }

    /**
     * 2. 管理端工作分身列表
     */
    @GetMapping("/shadows")
    public Result<Map<String, Object>> listSysUserShadows(
            @RequestHeader("Authorization") String authHeader) {
        return success(authService.listSysUserShadows(authHeader));
    }

    /**
     * 3. 管理端工作分身切换
     */
    @PostMapping("/switch-shadow")
    public Result<Map<String, Object>> switchShadow(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SwitchShadowRequest request) {
        return success("切换成功", authService.switchShadow(authHeader, request));
    }

    /**
     * 4. C端业主跨小区多租户切换接口
     */
    @PostMapping("/switch-tenant")
    public Result<Map<String, Object>> switchTenant(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SwitchTenantRequest request) {
        return success("切换成功", authService.switchTenant(authHeader, request));
    }
}
