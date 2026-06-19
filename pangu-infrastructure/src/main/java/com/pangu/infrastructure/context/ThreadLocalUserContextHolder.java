package com.pangu.infrastructure.context;

import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@link UserContextHolder} 的 ThreadLocal 实现。
 *
 * <p>由 {@code JwtAuthenticationFilter} 在请求入口调用 {@link #set(UserContext)}，
 * 在 finally 块调用 {@link #clear()}。
 */
@Component
public class ThreadLocalUserContextHolder implements UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    @Override
    public UserContext current() {
        return HOLDER.get();
    }

    @Override
    public void set(UserContext context) {
        if (context == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(context);
        }
    }

    @Override
    public void clear() {
        HOLDER.remove();
    }
}
