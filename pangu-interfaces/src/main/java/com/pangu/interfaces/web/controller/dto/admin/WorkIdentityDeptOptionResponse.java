package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.WorkIdentityDeptOption;

/**
 * 工作身份部门选项响应。
 */
public record WorkIdentityDeptOptionResponse(
        Long deptId,
        Long parentId,
        String ancestors,
        String deptName,
        Integer deptType,
        String deptCategory,
        Long tenantId) {

    public static WorkIdentityDeptOptionResponse from(WorkIdentityDeptOption option) {
        return new WorkIdentityDeptOptionResponse(
                option.deptId(),
                option.parentId(),
                option.ancestors(),
                option.deptName(),
                option.deptType(),
                option.deptCategory(),
                option.tenantId());
    }
}
