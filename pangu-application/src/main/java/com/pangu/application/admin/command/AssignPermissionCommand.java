package com.pangu.application.admin.command;

/**
 * 角色 ↔ permission 授予命令。
 *
 * @param roleId        目标角色主键
 * @param permissionKey {@code sys_permission.permission_key}
 * @param grantedBy     操作人 user_id；从 SecurityUtils 注入
 */
public record AssignPermissionCommand(
        Long roleId,
        String permissionKey,
        Long grantedBy) {}
