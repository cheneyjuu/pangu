package com.pangu.interfaces.web.interceptor;

import com.pangu.domain.context.TenantContext;
import com.pangu.interfaces.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 多租户上下文 Web 拦截器
 * 自动从 HTTP 请求头的 Authorization Token 中解析租户（小区）ID，写入 ThreadLocal 上下文
 */
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    Long tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    }
                }
            } catch (Exception e) {
                // Token 解析失败时不中断请求（由 Security 过滤器进行认证拦截判定），此处仅静默提取租户ID
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束后必须清理 ThreadLocal 线程上下文，防止 Tomcat 线程池复用导致内存泄露及租户越权
        TenantContext.clear();
    }
}
