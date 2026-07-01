package com.pangu.application.support;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Application 层角色守卫。业务服务只提供错误码映射，当前身份读取与错误文案统一在这里。
 */
public final class ApplicationRoleGuard {

    private ApplicationRoleGuard() {
    }

    public static void requireRole(UserContextHolder userContextHolder,
                                   String expectedRole,
                                   String message,
                                   Function<String, ? extends RuntimeException> forbiddenMapper) {
        requireAnyRole(userContextHolder, Set.of(expectedRole), message, forbiddenMapper);
    }

    public static void requireAnyRole(UserContextHolder userContextHolder,
                                      Set<String> expectedRoles,
                                      String message,
                                      Function<String, ? extends RuntimeException> forbiddenMapper) {
        Objects.requireNonNull(userContextHolder, "userContextHolder must not be null");
        Objects.requireNonNull(expectedRoles, "expectedRoles must not be null");
        Objects.requireNonNull(forbiddenMapper, "forbiddenMapper must not be null");
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !expectedRoles.contains(ctx.roleKey())) {
            throw forbiddenMapper.apply(message + "，当前角色=" + currentRole(ctx));
        }
    }

    public static String currentRole(UserContext ctx) {
        return ctx == null ? "ANONYMOUS" : ctx.roleKey();
    }
}
