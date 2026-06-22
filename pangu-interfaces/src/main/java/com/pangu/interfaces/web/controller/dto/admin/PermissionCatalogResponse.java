package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.role.PermissionCatalog;

/**
 * 平台全量权限清单响应 DTO（M4-1 读侧，授权页勾选用）。
 */
public record PermissionCatalogResponse(
        String permissionKey,
        String description,
        String permissionGroup,
        String allowedDeptCategories,
        Integer isLegalRedline) {

    public static PermissionCatalogResponse from(PermissionCatalog c) {
        return new PermissionCatalogResponse(
                c.permissionKey(),
                c.description(),
                c.permissionGroup(),
                c.allowedDeptCategories(),
                c.isLegalRedline());
    }
}
