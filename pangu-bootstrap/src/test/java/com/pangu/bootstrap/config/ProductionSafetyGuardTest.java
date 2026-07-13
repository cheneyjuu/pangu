// 关联业务：验证生产环境不会在缺失微信小程序身份授权配置时启动，避免手机号授权服务以不安全配置运行。
package com.pangu.bootstrap.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSafetyGuardTest {

    @Test
    void nonProdProfile_allowsLocalDefaults() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertDoesNotThrow(() -> new ProductionSafetyGuard(environment).validate());
    }

    @Test
    void prodProfile_acceptsExplicitNonMockProviders() {
        MockEnvironment environment = validProdEnvironment();

        assertDoesNotThrow(() -> new ProductionSafetyGuard(environment).validate());
    }

    @Test
    void prodProfile_rejectsOutboxPendingAttestation() {
        MockEnvironment environment = validProdEnvironment()
                .withProperty("platform.attestation.provider", "outbox-pending");

        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(environment).validate());
    }

    @Test
    void prodProfile_rejectsMockSmsProvider() {
        MockEnvironment environment = validProdEnvironment()
                .withProperty("platform.voting.sms-provider-mode", "mock");

        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(environment).validate());
    }

    @Test
    void prodProfile_rejectsMockIdentityProviders() {
        MockEnvironment environment = validProdEnvironment()
                .withProperty("platform.identity.id-card-ocr.provider-mode", "mock");

        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(environment).validate());
    }

    @Test
    void prodProfile_rejectsDefaultSecrets() {
        MockEnvironment environment = validProdEnvironment()
                .withProperty("platform.security.jwt-secret", "pangu-secure-jwt-token-secret-key-32-chars-minimum");

        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(environment).validate());
    }

    @Test
    void prodProfile_rejectsMissingWeChatMiniProgramCredentials() {
        MockEnvironment environment = validProdEnvironment()
                .withProperty("platform.identity.wechat-mini-program.app-secret", "");

        assertThrows(IllegalStateException.class, () -> new ProductionSafetyGuard(environment).validate());
    }

    private MockEnvironment validProdEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.withProperty("platform.attestation.provider", "high-court")
                .withProperty("platform.committee-key-revocation.provider", "certificate-authority")
                .withProperty("platform.voting.sms-provider-mode", "http")
                .withProperty("platform.voting.sms-provider.endpoint", "https://sms.example.invalid/send")
                .withProperty("platform.identity.id-card-ocr.provider-mode", "tencent")
                .withProperty("platform.identity.face-auth.provider-mode", "tencent")
                .withProperty("platform.identity.tencent.secret-id", "PROD_TENCENT_SECRET_ID")
                .withProperty("platform.identity.tencent.secret-key", "PROD_TENCENT_SECRET_KEY")
                .withProperty("platform.identity.face-auth.tencent.rule-id", "prod-face-rule-id")
                .withProperty("platform.identity.wechat-mini-program.app-id", "wx-prod-mini-program")
                .withProperty("platform.identity.wechat-mini-program.app-secret", "prod-mini-program-secret")
                .withProperty("platform.security.jwt-secret", "prod-jwt-secret-with-enough-length")
                .withProperty("platform.security.sm4-key-hex", "fedcba98765432100123456789abcdef")
                .withProperty("platform.ali-oss.access-key-id", "PROD_KEY_ID")
                .withProperty("platform.ali-oss.access-key-secret", "PROD_KEY_SECRET")
                .withProperty("mybatis.configuration.log-impl", "org.apache.ibatis.logging.slf4j.Slf4jImpl");
        return environment;
    }
}
