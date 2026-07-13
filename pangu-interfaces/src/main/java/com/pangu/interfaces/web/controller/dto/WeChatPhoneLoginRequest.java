// 关联业务：业主端以微信原生手机号授权建立会话，前端仅提交一次性授权码，手机号由服务端向微信平台交换。
package com.pangu.interfaces.web.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** 微信小程序手机号授权登录请求。 */
public record WeChatPhoneLoginRequest(
        @NotBlank(message = "微信登录凭证不能为空") String loginCode,
        @NotBlank(message = "微信手机号授权凭证不能为空") String phoneCode) {
}
