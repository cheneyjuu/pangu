package com.pangu.domain.model.role;

import java.time.Instant;

/**
 * 某角色已授的权限明细行（读侧视图，framework-light record）。
 *
 * <p>{@code sys_role_permission} JOIN {@code sys_permission} 的聚合结果：
 * 前 5 个字段来自 {@code sys_permission}（权限元信息），后 2 个来自
 * {@code sys_role_permission}（授予审计）。预置映射的 {@code grantedBy/grantedAt}
 * 为 {@code null}（无操作人），SaaS 管理员通过 assignPermission 授予的才有值。
 *
 * @param permissionKey          权限业务键
 * @param description            权限描述
 * @param permissionGroup        权限组（ADMIN/VOTING/WAIVER/FUND/IDENTITY/OWNER 等）
 * @param allowedDeptCategories  端归属位组合（G/B/S/GB/GBS）
 * @param isLegalRedline         1=法理红线（要求 fixed_data_scope NOT NULL）
 * @param grantedBy              授予人 user_id；预置为 null
 * @param grantedAt              授予时间；预置为 null
 */
public record RolePermissionDetail(
        String permissionKey,
        String description,
        String permissionGroup,
        String allowedDeptCategories,
        Integer isLegalRedline,
        Long grantedBy,
        Instant grantedAt) {
}
