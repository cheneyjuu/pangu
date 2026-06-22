package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 角色列表行映射（读侧，含 permission_count 子查询列）。
 *
 * <p>对应 {@code SysRoleMapper.pageRolesList} 的结果集：在 {@link SysRoleRow} 字段基础上
 * 补 {@code permissionCount}（子查询 COUNT 聚合）。
 */
@Data
public class RoleListItemRow {
    private Long roleId;
    private String roleKey;
    private String roleName;
    private String allowedDeptCategory;
    private String fixedDataScope;
    private String defaultDataScope;
    private Integer isSystem;
    private String status;
    private Long permissionCount;
    private Instant createTime;
}
