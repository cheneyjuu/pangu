package com.pangu.interfaces.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 签发与解析（M1 RBAC 重构后版本，JJWT 0.12.x）。
 *
 * <p>设计要点：JWT 仅嵌入「身份三元组」：
 * <ul>
 *   <li>{@code sub} = {@code account_id}（自然人主体 ID，审计主键）；</li>
 *   <li>claim {@code identityType} = {@code SYS_USER} 或 {@code C_USER}；</li>
 *   <li>claim {@code activeIdentityId} = {@code sys_user.user_id} 或 {@code c_user.uid}；</li>
 *   <li>claim {@code tenantId} = 当前激活的小区租户 ID（可为 null，表示街道办俯瞰）。</li>
 * </ul>
 *
 * <p>**不嵌入** roles / permissions —— 角色与能力点变更需要立即生效，
 * 由 {@code UserContextLoader} 在每次请求实时反查（M2 引入 Redis 5min TTL）。
 */
@Component
public class JwtTokenProvider {

    @Value("${platform.security.jwt-secret}")
    private String jwtSecret;

    @Value("${platform.security.jwt-expiration-seconds}")
    private long jwtExpirationInSeconds;

    private SecretKey getSigningKey() {
        byte[] keyBytes = this.jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发 JWT。
     *
     * @param accountId         自然人主体 ID（{@code t_account.account_id}）
     * @param identityType      "SYS_USER" / "C_USER"
     * @param activeIdentityId  {@code sys_user.user_id} 或 {@code c_user.uid}
     * @param tenantId          当前激活租户 ID；可为 null
     */
    public String generateToken(Long accountId, String identityType, Long activeIdentityId, Long tenantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim("identityType", identityType)
                .claim("activeIdentityId", activeIdentityId)
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getAccountIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getIdentityTypeFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("identityType", String.class);
    }

    public Long getActiveIdentityIdFromToken(String token) {
        Claims claims = parseToken(token);
        Number n = claims.get("activeIdentityId", Number.class);
        return n == null ? null : n.longValue();
    }

    public Long getTenantIdFromToken(String token) {
        Claims claims = parseToken(token);
        Number n = claims.get("tenantId", Number.class);
        return n == null ? null : n.longValue();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
