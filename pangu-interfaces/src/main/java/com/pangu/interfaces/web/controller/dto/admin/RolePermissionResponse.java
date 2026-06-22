package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.role.RolePermissionDetail;

import java.time.Instant;

/**
 * 某角色已授权限明细响应 DTO（M4-1 读侧）。
 *
 * <p>{@code sys_role_permission} JOIN {@code sys_permission} 的聚合结果投影。
 * {@code grantedBy/grantedAt} 对预置映射为 {@code null}。
 */
public record RolePermissionResponse(
        String permissionKey,
        String description,
        String permissionGroup,
        String allowedDeptCategories,
        Integer isLegalRedline,
        Long grantedBy,
        Instant grantedAt) {

    public static RolePermissionResponse from(RolePermissionDetail d) {
        return new RolePermissionResponse(
                d.permissionKey(),
                d.description(),
                d.permissionGroup(),
                d.allowedDeptCategories(),
                d.isLegalRedline(),
                d.grantedBy(),
                d.grantedAt());
    }
}
