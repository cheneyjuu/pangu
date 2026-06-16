package com.pangu.interfaces.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT Token 签发与解析提供者 (基于 JJWT 0.12.x 标准)
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
     * 为用户签发增强型 JWT Token
     * @param uid 自然人唯一标识
     * @param tenantId 当前激活的小区租户 ID
     * @param roles 角色列表
     * @param permissions 权限权限字符列表
     * @param userType 用户类型（1-业主, 2-物业...）
     * @return 紧凑的 JWT 字符串
     */
    public String generateToken(Long uid, Long tenantId, List<String> roles, List<String> permissions, Integer userType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInSeconds * 1000);

        return Jwts.builder()
                .subject(String.valueOf(uid))
                .claim("tenantId", tenantId)
                .claim("userType", userType)
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 校验 JWT Token 并解析获取其全部载荷 Claims
     * @param token JWT 字符串
     * @return 载荷
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中直接提取 UID
     */
    public Long getUidFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 Token 中直接提取租户 ID
     */
    public Long getTenantIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("tenantId", Long.class);
    }

    /**
     * 校验 Token 是否过期
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
