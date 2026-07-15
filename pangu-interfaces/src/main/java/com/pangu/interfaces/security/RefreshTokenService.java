// 关联业务：签发和轮换登录刷新凭证，使访问 JWT 到期后可在服务端核验身份上下文后续期。
package com.pangu.interfaces.security;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.repository.RefreshSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 刷新凭证服务。
 *
 * <p>刷新凭证为高熵随机值，只向客户端返回一次；数据库保存 SHA-256 摘要并由仓储原子消费，
 * 因此无法从数据库记录还原客户端凭证。</p>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int MIN_TOKEN_LENGTH = 40;
    private static final int MAX_TOKEN_LENGTH = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshSessionRepository refreshSessionRepository;

    @Value("${platform.security.refresh-token-expiration-seconds:1209600}")
    private long refreshTokenExpirationSeconds;

    /** 为当前身份上下文签发新的、可轮换的刷新凭证。 */
    public IssuedRefreshToken issue(UserContext context) {
        String rawToken = generateToken();
        refreshSessionRepository.create(new RefreshSessionRepository.NewRefreshSession(
                hash(rawToken),
                context.accountId(),
                context.identityType().name(),
                context.activeIdentityId(),
                context.tenantId(),
                refreshTokenExpirationSeconds));
        return new IssuedRefreshToken(rawToken, refreshTokenExpirationSeconds);
    }

    /**
     * 消费刷新凭证；无效、过期或已轮换的凭证统一返回 {@code null}，避免向客户端泄露状态。
     */
    public RefreshSessionRepository.RefreshSession consume(String rawToken) {
        if (rawToken == null || rawToken.length() < MIN_TOKEN_LENGTH || rawToken.length() > MAX_TOKEN_LENGTH) {
            return null;
        }
        return refreshSessionRepository.consume(hash(rawToken));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256 摘要算法", exception);
        }
    }

    public record IssuedRefreshToken(String token, long expiresInSeconds) {
    }
}
