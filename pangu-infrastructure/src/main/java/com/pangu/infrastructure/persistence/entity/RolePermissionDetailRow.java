package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 角色已授权限明细行映射（{@code sys_role_permission} JOIN {@code sys_permission}）。
 */
@Data
public class RolePermissionDetailRow {
    private String permissionKey;
    private String description;
    private String permissionGroup;
    private String allowedDeptCategories;
    private Integer isLegalRedline;
    private Long grantedBy;
    private Instant grantedAt;
}
