package com.pangu.domain.gateway.identity;

public interface FaceAuthGateway {

    String PROVIDER_TENCENT_FACEID = "TENCENT_FACEID";

    FaceAuthSession createSession(FaceAuthSessionRequest request);

    FaceAuthVerificationResult verify(FaceAuthVerificationRequest request);

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
