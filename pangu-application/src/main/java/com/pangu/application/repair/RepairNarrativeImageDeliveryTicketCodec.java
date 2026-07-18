// 关联业务：为业主端维修方案正文图片签发同源、短时且不可伪造的公开读取凭证。
package com.pangu.application.repair;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static com.pangu.application.repair.RepairWorkOrderApplicationException.Reason.FORBIDDEN;

@Component
public final class RepairNarrativeImageDeliveryTicketCodec {

    static final Duration TICKET_VALIDITY = Duration.ofMinutes(10);
    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] PURPOSE =
            "pangu:repair-narrative-image-delivery:v1\n".getBytes(StandardCharsets.UTF_8);

    private final byte[] signingKey;
    private final String publicApiBaseUrl;
    private final Clock clock;

    @Autowired
    public RepairNarrativeImageDeliveryTicketCodec(
            @Value("${platform.security.jwt-secret}") String signingSecret,
            @Value("${platform.public-api-base-url}") String publicApiBaseUrl) {
        this(signingSecret, publicApiBaseUrl, Clock.systemUTC());
    }

    RepairNarrativeImageDeliveryTicketCodec(
            String signingSecret,
            String publicApiBaseUrl,
            Clock clock) {
        if (signingSecret == null || signingSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("维修正文图片凭证签名密钥至少需要 32 字节");
        }
        this.signingKey = signingSecret.getBytes(StandardCharsets.UTF_8).clone();
        this.publicApiBaseUrl = normalizedPublicApiBaseUrl(publicApiBaseUrl);
        this.clock = clock;
    }

    DeliveryTicket issue(Long imageId, Long planId, Long tenantId) {
        requirePositive(imageId, "imageId");
        requirePositive(planId, "planId");
        requirePositive(tenantId, "tenantId");
        Instant expiresAt = clock.instant().plus(TICKET_VALIDITY);
        String payload = String.join(":",
                VERSION,
                imageId.toString(),
                planId.toString(),
                tenantId.toString(),
                Long.toString(expiresAt.getEpochSecond()));
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String token = encodedPayload + "." + encode(sign(encodedPayload));
        String deliveryUrl = publicApiBaseUrl + "/public/repair-plan-images/" + imageId
                + "?ticket=" + token;
        return new DeliveryTicket(deliveryUrl, expiresAt);
    }

    TicketClaims verify(Long requestedImageId, String token) {
        if (token == null || token.isBlank()) {
            throw forbidden();
        }
        String[] tokenParts = token.split("\\.", -1);
        if (tokenParts.length != 2 || tokenParts[0].isBlank() || tokenParts[1].isBlank()) {
            throw forbidden();
        }
        byte[] suppliedSignature;
        String payload;
        try {
            suppliedSignature = Base64.getUrlDecoder().decode(tokenParts[1]);
            payload = new String(
                    Base64.getUrlDecoder().decode(tokenParts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw forbidden();
        }
        if (!MessageDigest.isEqual(sign(tokenParts[0]), suppliedSignature)) {
            throw forbidden();
        }
        String[] claims = payload.split(":", -1);
        if (claims.length != 5 || !VERSION.equals(claims[0])) {
            throw forbidden();
        }
        try {
            Long imageId = Long.valueOf(claims[1]);
            Long planId = Long.valueOf(claims[2]);
            Long tenantId = Long.valueOf(claims[3]);
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(claims[4]));
            requirePositive(imageId, "imageId");
            requirePositive(planId, "planId");
            requirePositive(tenantId, "tenantId");
            if (!imageId.equals(requestedImageId) || !expiresAt.isAfter(clock.instant())) {
                throw forbidden();
            }
            return new TicketClaims(imageId, planId, tenantId, expiresAt);
        } catch (NumberFormatException ex) {
            throw forbidden();
        }
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            mac.update(PURPOSE);
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            throw new IllegalStateException("无法签发维修正文图片访问凭证", ex);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String normalizedPublicApiBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("platform.public-api-base-url 必填");
        }
        URI uri = URI.create(value.trim());
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp)
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "platform.public-api-base-url 必须是 HTTPS 地址；仅本机开发允许 HTTP");
        }
        String normalized = uri.toString();
        return normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " 必须为正整数");
        }
    }

    private RepairWorkOrderApplicationException forbidden() {
        return new RepairWorkOrderApplicationException(FORBIDDEN, "维修正文图片访问凭证无效或已过期");
    }

    record DeliveryTicket(String url, Instant expiresAt) {
    }

    record TicketClaims(Long imageId, Long planId, Long tenantId, Instant expiresAt) {
    }
}
