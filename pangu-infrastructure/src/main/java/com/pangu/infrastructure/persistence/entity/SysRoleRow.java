package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * {@code sys_role} 行映射；trigger 7 对 BEFORE DELETE 拦截 is_system=1 的预置角色，
 * 由 {@code SysRoleRepositoryImpl} 翻译为 domain port 异常。
 */
@Data
public class SysRoleRow {
    private Long roleId;
    private String roleName;
    private String roleKey;
    private String allowedDeptCategory;
    private String fixedDataScope;
    private String defaultDataScope;
    private Integer isSystem;
    private String status;
    private Instant createTime;
    private Instant updateTime;
}
