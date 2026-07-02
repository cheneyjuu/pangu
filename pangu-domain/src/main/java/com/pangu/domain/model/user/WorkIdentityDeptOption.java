package com.pangu.domain.model.user;

/**
 * 新增工作身份时可选择的部门。
 */
public record WorkIdentityDeptOption(
        Long deptId,
        Long parentId,
        String ancestors,
        String deptName,
        Integer deptType,
        String deptCategory,
        Long tenantId) {
}
