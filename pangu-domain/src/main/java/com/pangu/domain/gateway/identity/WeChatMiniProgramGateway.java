// 关联业务：业主端小程序以微信手机号授权建立自然人会话，服务端只接收微信换取后的可信手机号和不可逆主体标识。
package com.pangu.domain.gateway.identity;

/**
 * 微信小程序身份授权端口。
 *
 * <p>小程序的 {@code loginCode} 与手机号授权 {@code phoneCode} 均只能由服务端交换，
 * 应用层不会持有 AppSecret、session_key 或原始 openid。返回的主体标识已不可逆散列，
 * 仅用于同一小程序内的账号绑定与防串号校验。</p>
 */
public interface WeChatMiniProgramGateway {

    /** 当前小程序 AppId；未配置时返回空字符串，供非小程序会话安全降级展示。 */
    String miniProgramAppId();

    /**
     * 交换一次微信登录和手机号授权，返回已由微信平台验证的中国大陆手机号。
     */
    WeChatPhoneIdentity exchangePhoneAuthorization(String loginCode, String phoneCode);

    record WeChatPhoneIdentity(String phoneNumber, String subjectHash) {
    }

    enum FailureType {
        CONFIGURATION,
        INVALID_AUTHORIZATION,
        TEMPORARILY_UNAVAILABLE
    }

    /**
     * 对外服务商错误的受控语义；禁止把授权码、AppSecret 或上游原始响应带回客户端。
     */
    final class WeChatAuthorizationException extends RuntimeException {
        private final FailureType failureType;

        public WeChatAuthorizationException(FailureType failureType, String message) {
            super(message);
            this.failureType = failureType;
        }

        public WeChatAuthorizationException(FailureType failureType, String message, Throwable cause) {
            super(message, cause);
            this.failureType = failureType;
        }

        public FailureType failureType() {
            return failureType;
        }
    }
}
