package com.pangu.domain.gateway.identity;

public interface FaceAuthGateway {

    String PROVIDER_TENCENT_FACEID = "TENCENT_FACEID";

    FaceAuthSession createSession(FaceAuthSessionRequest request);

    FaceAuthVerificationResult verify(FaceAuthVerificationRequest request);

    /**
     * 是否仅用于开发或体验环境的测试采集。
     *
     * <p>测试网关可以验证客户端的交互链路，但不得据此提升业主实名认证等级。
     */
    default boolean isTestOnly() {
        return false;
    }

    record FaceAuthSessionRequest(String realName, String idCardNumber, String extra) {
    }

    record FaceAuthSession(
            String provider,
            String bizToken,
            String url,
            String requestId,
            long expiresInSeconds
    ) {
    }

    record FaceAuthVerificationRequest(String provider, String bizToken, String expectedRealName, String expectedIdCardNumber) {
    }

    record FaceAuthVerificationResult(
            boolean verified,
            String provider,
            String providerRequestId,
            String providerResult,
            String requestId,
            String failureReason
    ) {
    }
}
