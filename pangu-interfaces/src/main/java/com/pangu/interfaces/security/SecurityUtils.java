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

    /**
     * 获取当前用户的后台用户 ID（sys_user.user_id）；纯 C 端业主无 sys_user 时退化为 uid。
     */
    public static Long getUserId() {
        UserSecurityContext ctx = getUserContext();
        return ctx != null ? ctx.getUserId() : null;
    }

    /**
     * 获取当前用户的部门类型 (sys_dept.dept_type)：1=街道办，2=居委会，3=物业，业主等纯 C 端为 null。
     */
    public static Integer getDeptType() {
        UserSecurityContext ctx = getUserContext();
        return ctx != null ? ctx.getDeptType() : null;
    }
}
