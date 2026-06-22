package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

/**
 * 平台全量权限清单行映射（{@code sys_permission}）。
 */
@Data
public class PermissionCatalogRow {
    private String permissionKey;
    private String description;
    private String permissionGroup;
    private String allowedDeptCategories;
    private Integer isLegalRedline;
}
