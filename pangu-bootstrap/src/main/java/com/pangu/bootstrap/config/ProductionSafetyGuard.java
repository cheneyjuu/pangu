// 关联业务：生产环境仅在微信小程序身份授权等关键外部服务完成安全配置后启动，防止以开发默认值处理个人信息。
package com.pangu.bootstrap.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProductionSafetyGuard {

    private static final String DEV_JWT_SECRET = "pangu-secure-jwt-token-secret-key-32-chars-minimum";
    private static final String DEV_SM4_KEY = "0123456789abcdeffedcba9876543210";
    private static final Set<String> NON_LEGAL_ATTESTATION_PROVIDERS = Set.of(
            "outbox-pending", "outbox_pending", "stub", "mock");

    private final Environment environment;

    @PostConstruct
    void validate() {
        if (!isProdProfile()) {
            return;
        }

        rejectValueIn("platform.attestation.provider", NON_LEGAL_ATTESTATION_PROVIDERS,
                "生产环境禁止使用司法链 outbox/stub/mock provider");
        rejectValue("platform.committee-key-revocation.provider", "mock",
                "生产环境禁止使用老主任密钥回收 mock provider");
        requireValue("platform.voting.sms-provider-mode", "http",
                "生产环境必须使用真实短信 provider mode=http");
        requireText("platform.voting.sms-provider.endpoint", "生产环境必须配置真实短信 provider endpoint");
        requireValue("platform.identity.id-card-ocr.provider-mode", "tencent",
                "生产环境必须使用真实身份证 OCR provider mode=tencent");
        requireValue("platform.identity.face-auth.provider-mode", "tencent",
                "生产环境必须使用真实人脸核身 provider mode=tencent");
        requireText("platform.identity.tencent.secret-id", "生产环境必须配置腾讯云身份核验 secret-id");
        requireText("platform.identity.tencent.secret-key", "生产环境必须配置腾讯云身份核验 secret-key");
        requireText("platform.identity.face-auth.tencent.rule-id", "生产环境必须配置腾讯云实名核身 rule-id");
        requireText("platform.identity.wechat-mini-program.app-id", "生产环境必须配置微信小程序 app-id");
        requireText("platform.identity.wechat-mini-program.app-secret", "生产环境必须配置微信小程序 app-secret");
        rejectValue("platform.security.jwt-secret", DEV_JWT_SECRET,
                "生产环境禁止使用默认 JWT secret");
        rejectValue("platform.security.sm4-key-hex", DEV_SM4_KEY,
                "生产环境禁止使用默认 SM4 key");
        requireHex32("platform.security.sm4-key-hex", "生产环境 SM4 key 必须是 32 位十六进制字符串");
        rejectValue("platform.ali-oss.access-key-id", "DUMMY_KEY_ID_FOR_DEV",
                "生产环境禁止使用默认 OSS access-key-id");
        rejectValue("platform.ali-oss.access-key-secret", "DUMMY_KEY_SECRET_FOR_DEV",
                "生产环境禁止使用默认 OSS access-key-secret");
        rejectValue("mybatis.configuration.log-impl", "org.apache.ibatis.logging.stdout.StdOutImpl",
                "生产环境禁止使用 MyBatis stdout SQL 日志");
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
    }

    private void requireText(String key, String message) {
        String value = environment.getProperty(key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message + ": " + key);
        }
    }

    private void requireValue(String key, String expected, String message) {
        String value = normalized(environment.getProperty(key));
        if (!expected.equals(value)) {
            throw new IllegalStateException(message + ": " + key + "=" + value);
        }
    }

    private void rejectValue(String key, String rejected, String message) {
        String value = normalized(environment.getProperty(key));
        if (normalized(rejected).equals(value)) {
            throw new IllegalStateException(message + ": " + key);
        }
    }

    private void rejectValueIn(String key, Set<String> rejectedValues, String message) {
        String value = normalized(environment.getProperty(key));
        if (!StringUtils.hasText(value) || rejectedValues.contains(value)) {
            throw new IllegalStateException(message + ": " + key + "=" + value);
        }
    }

    private void requireHex32(String key, String message) {
        String value = environment.getProperty(key);
        if (value == null || !value.matches("^[0-9a-fA-F]{32}$")) {
            throw new IllegalStateException(message + ": " + key);
        }
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
