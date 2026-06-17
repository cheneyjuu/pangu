package com.pangu.domain.context;

/**
 * 用户上下文获取端口（Hexagonal Port）。
 *
 * <p>application/domain 通过本接口读取当前线程的 {@link UserContext}，
 * 而不直接 import Spring Security。
 *
 * <p>infrastructure 适配器 {@code SecurityUtilsUserContextHolder} 桥接到
 * Spring Security 的 {@code SecurityContextHolder}。
 *
 * <p>未登录时返回 {@code null}；上层应按「无权限」处理。
 */
public interface UserContextHolder {

    UserContext current();
}
