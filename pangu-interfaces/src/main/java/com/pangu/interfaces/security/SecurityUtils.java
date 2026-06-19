package com.pangu.interfaces.security;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;

/**
 * 安全上下文操作工具类（M1 RBAC 重构后版本）。
 *
 * <p>设计职责：把 {@link UserContextHolder}（ThreadLocal Bean）封装为静态访问入口，
 * 让 controller / service 无需直接 import Holder Bean 即可读取上下文。
 *
 * <p>历史包袱：曾经直接读 Spring Security {@code SecurityContextHolder} 中的
 * {@code DataScopeInterceptor.UserSecurityContext}；M1 已剥离该内部类，
 * 改为以 {@link UserContext} 作为唯一上下文契约。
 */
public final class SecurityUtils {

    private static UserContextHolder holder;

    /** Spring 启动时由 {@link SecurityUtilsHolderInjector} 注入。 */
    static void inject(UserContextHolder injected) {
        SecurityUtils.holder = injected;
    }

    private SecurityUtils() {
    }

    /** @return 当前上下文；未登录时为 null。 */
    public static UserContext getUserContext() {
        return holder != null ? holder.current() : null;
    }

    /** @return 自然人 {@code account_id}（JWT sub）；未登录时为 null。 */
    public static Long getAccountId() {
        UserContext ctx = getUserContext();
        return ctx != null ? ctx.accountId() : null;
    }

    /** @return 当前活跃 {@code sys_user.user_id}；非 SYS_USER 身份返回 null。 */
    public static Long getUserId() {
        UserContext ctx = getUserContext();
        return ctx != null ? ctx.userId() : null;
    }

    /** @return 当前活跃 {@code c_user.uid}；非 C_USER 身份返回 null。 */
    public static Long getUid() {
        UserContext ctx = getUserContext();
        return ctx != null ? ctx.uid() : null;
    }

    /** @return 当前激活的小区租户 ID；街道办俯瞰场景为 null。 */
    public static Long getTenantId() {
        UserContext ctx = getUserContext();
        return ctx != null ? ctx.tenantId() : null;
    }
}
