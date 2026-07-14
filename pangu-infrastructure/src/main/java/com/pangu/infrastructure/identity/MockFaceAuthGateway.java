package com.pangu.infrastructure.identity;

import com.pangu.domain.gateway.identity.FaceAuthGateway;
import com.pangu.domain.model.identity.ChineseResidentId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "platform.identity.face-auth.provider-mode", havingValue = "mock", matchIfMissing = true)
public class MockFaceAuthGateway implements FaceAuthGateway {

    /**
     * Mock 网关仅供开发、体验环境验证摄像头采集及回执链路，不能作为活体核验结果。
     */
    @Override
    public boolean isTestOnly() {
        return true;
    }

    @Override
    public FaceAuthSession createSession(FaceAuthSessionRequest request) {
        String tokenSeed = Math.abs((request.realName() + ":" + request.idCardNumber()).hashCode()) + "";
        return new FaceAuthSession(
                PROVIDER_TENCENT_FACEID,
                "mock-face-biz-token-" + tokenSeed,
                "weixin://mock-face-auth/" + tokenSeed,
                "mock-face-session-" + tokenSeed,
                7200);
    }

    @Override
    public FaceAuthVerificationResult verify(FaceAuthVerificationRequest request) {
        String bizToken = request == null ? null : request.bizToken();
        boolean usableToken = StringUtils.hasText(bizToken) && bizToken.trim().startsWith("mock-face-biz-token-");
        boolean identityOk = request != null
                && StringUtils.hasText(request.expectedRealName())
                && ChineseResidentId.isValid(request.expectedIdCardNumber());
        if (!usableToken || !identityOk) {
            return new FaceAuthVerificationResult(false, PROVIDER_TENCENT_FACEID, bizToken,
                    "{\"mockVerified\":false}", "mock-face-verify", "mock face auth token or identity invalid");
        }
        return new FaceAuthVerificationResult(true, PROVIDER_TENCENT_FACEID, bizToken,
                "{\"mockVerified\":true}", "mock-face-verify", null);
    }
}
