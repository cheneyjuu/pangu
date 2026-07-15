// 关联业务：客户端以不透明刷新凭证续期访问 JWT，不允许客户端传入或伪造会话身份上下文。
package com.pangu.interfaces.web.controller.dto;

import jakarta.validation.constraints.NotBlank;

/** JWT 续期请求。 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken 不能为空") String refreshToken) {
}
