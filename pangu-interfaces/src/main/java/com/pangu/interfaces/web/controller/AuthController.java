// 关联业务：统一承接平台短信登录与微信小程序原生手机号授权，服务端负责身份交换和会话签发。
package com.pangu.interfaces.web.controller;

import com.pangu.interfaces.web.controller.dto.LoginRequest;
import com.pangu.interfaces.web.controller.dto.NavMenuResponse;
import com.pangu.interfaces.web.controller.dto.SwitchShadowRequest;
import com.pangu.interfaces.web.controller.dto.SwitchTenantRequest;
import com.pangu.interfaces.web.controller.dto.WeChatPhoneLoginRequest;
import com.pangu.interfaces.web.controller.dto.WeChatProfileRequest;
import com.pangu.interfaces.web.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
     * 2. 小程序微信手机号授权登录。
     * 微信临时凭证仅由服务端交换，客户端不持有 AppSecret、session_key 或原始 openid。
     */
    @PostMapping("/wechat-phone-login")
    public Result<Map<String, Object>> weChatPhoneLogin(@Valid @RequestBody WeChatPhoneLoginRequest request) {
        return success(authService.weChatPhoneLogin(request));
    }

    /**
     * 3. 保存用户额外确认授权的微信昵称与头像；不作为实名或表决资格依据。
     */
    @PostMapping("/wechat-profile")
    public Result<Map<String, Object>> updateWeChatProfile(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody WeChatProfileRequest request) {
        return success(authService.updateWeChatProfile(authHeader, request));
    }

    /**
     * 4. 管理端工作分身列表
     */
    @GetMapping("/shadows")
    public Result<Map<String, Object>> listSysUserShadows(
            @RequestHeader("Authorization") String authHeader) {
        return success(authService.listSysUserShadows(authHeader));
    }

    /**
     * 5. 当前身份可见管理端菜单
     */
    @GetMapping("/menus")
    public Result<List<NavMenuResponse>> listMenus(
            @RequestHeader("Authorization") String authHeader) {
        return success(authService.listMenus(authHeader));
    }

    /**
     * 6. 管理端工作分身切换
     */
    @PostMapping("/switch-shadow")
    public Result<Map<String, Object>> switchShadow(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SwitchShadowRequest request) {
        return success("切换成功", authService.switchShadow(authHeader, request));
    }

    /**
     * 7. C端业主跨小区多租户切换接口
     */
    @PostMapping("/switch-tenant")
    public Result<Map<String, Object>> switchTenant(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SwitchTenantRequest request) {
        return success("切换成功", authService.switchTenant(authHeader, request));
    }

    /**
     * 8. G 端街镇或平台根组织可监管小区列表。
     */
    @GetMapping("/managed-communities")
    public Result<Map<String, Object>> listManagedCommunities(
            @RequestHeader("Authorization") String authHeader) {
        return success(authService.listManagedCommunities(authHeader));
    }

    /**
     * 9. G 端辖区小区上下文切换，后端校验组织授权后重签 JWT。
     */
    @PostMapping("/switch-managed-community")
    public Result<Map<String, Object>> switchManagedCommunity(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SwitchTenantRequest request) {
        return success("切换成功", authService.switchManagedCommunity(authHeader, request));
    }
}
