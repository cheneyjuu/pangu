package com.pangu.application.admin.command;

/**
 * 创建角色命令（SaaS 管理员后台）。
 *
 * @param roleKey             业务键（唯一），如 {@code TEST_DEMO_ROLE}
 * @param roleName            展示名
 * @param allowedDeptCategory 端归属：'G' / 'B' / 'S'
 * @param fixedDataScope      法理红线锁死 effective_data_scope；可空（非红线角色）
 * @param defaultDataScope    默认 effective_data_scope；不可空
 */
public record CreateRoleCommand(
        String roleKey,
        String roleName,
        String allowedDeptCategory,
        String fixedDataScope,
        String defaultDataScope) {}
