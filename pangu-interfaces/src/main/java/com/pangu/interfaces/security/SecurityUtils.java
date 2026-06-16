package com.pangu.interfaces.security;

import com.pangu.infrastructure.persistence.handler.DataScopeInterceptor.UserSecurityContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文操作工具类 (解耦表现层与安全框架核心细节)
 */
public class SecurityUtils {

    /**
     * 获取当前登录用户的安全上下文 UserSecurityContext
     */
    public static UserSecurityContext getUserContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserSecurityContext) {
            return (UserSecurityContext) auth.getPrincipal();
        }
        return null;
    }
    
    /**
     * 获取当前登录用户的自然人 UID
     */
    public static Long getUid() {
        UserSecurityContext ctx = getUserContext();
        return ctx != null ? ctx.getUid() : null;
    }

    /**
     * 获取当前用户的租户 ID
     */
    public static Long getTenantId() {
        UserSecurityContext ctx = getUserContext();
        return ctx != null ? ctx.getTenantId() : null;
    }
}
