package com.pangu.interfaces.security;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.context.UserContextLoader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（M1 RBAC 重构后版本）。
 *
 * <p>职责：
 * <ol>
 *   <li>解析 {@code Authorization: Bearer ...} 请求头；</li>
 *   <li>校验 JWT 签名 + 过期；</li>
 *   <li>调用 {@link UserContextLoader#load(Long, UserContext.IdentityType, Long, Long)}
 *       基于 JWT 三元组（accountId / identityType / activeIdentityId）+ tenantId 提示
 *       一次性装配 {@link UserContext}；</li>
 *   <li>把 UserContext 写入 {@link UserContextHolder}（ThreadLocal），
 *       并将其 {@code permissions} 转换为 {@link SimpleGrantedAuthority}
 *       注入 Spring Security {@code Authentication}，让
 *       {@code @PreAuthorize("hasAuthority('waiver:approve:committee')")} 工作；</li>
 *   <li>过滤链处理完后在 finally 调用 {@link UserContextHolder#clear()}
 *       避免线程池泄漏。</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserContextLoader userContextLoader;

    @Autowired
    private UserContextHolder userContextHolder;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        boolean populated = false;
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtTokenProvider.validateToken(token)
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
                    String identityTypeStr = jwtTokenProvider.getIdentityTypeFromToken(token);
                    Long activeIdentityId = jwtTokenProvider.getActiveIdentityIdFromToken(token);
                    Long tenantIdHint = jwtTokenProvider.getTenantIdFromToken(token);

                    if (accountId != null && identityTypeStr != null && activeIdentityId != null) {
                        UserContext.IdentityType identityType = parseIdentityType(identityTypeStr);
                        if (identityType != null) {
                            UserContext ctx = userContextLoader.load(
                                    accountId, identityType, activeIdentityId, tenantIdHint);
                            if (ctx != null) {
                                userContextHolder.set(ctx);
                                populated = true;
                                List<GrantedAuthority> authorities = ctx.permissions().stream()
                                        .map(p -> (GrantedAuthority) new SimpleGrantedAuthority(p))
                                        .toList();
                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(ctx, token, authorities);
                                authentication.setDetails(
                                        new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }
                        }
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            if (populated) {
                userContextHolder.clear();
            }
        }
    }

    private UserContext.IdentityType parseIdentityType(String s) {
        try {
            return UserContext.IdentityType.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
