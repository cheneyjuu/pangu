package com.pangu.domain.context;

/**
 * 用户上下文获取 / 设置端口（Hexagonal Port）。
 *
 * <p>application/domain 通过本接口读取当前线程的 {@link UserContext}，
 * 而不直接 import Spring Security。
 *
 * <p>infrastructure 适配器（{@code ThreadLocalUserContextHolder}）以
 * Spring Bean + ThreadLocal 实现：
 * <ul>
 *   <li>{@link #current()} 读取当前线程上下文；未登录返回 {@code null}；</li>
 *   <li>{@link #set(UserContext)} 由 {@code JwtAuthenticationFilter} 在过滤链入口装配；</li>
 *   <li>{@link #clear()} 必须在 {@code finally} 调用，避免线程池泄漏。</li>
 * </ul>
 */
public interface UserContextHolder {

    /** @return 当前线程上下文；未登录返回 {@code null}。 */
    UserContext current();

    /** 写入当前线程上下文（请求开始时由过滤器装配）。 */
    void set(UserContext context);

    /** 清空当前线程上下文（请求结束时必须调用）。 */
    void clear();
}
