package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.WorkIdentityShadow;

import java.util.List;

/**
 * 管理端工作身份响应。
 */
public record WorkIdentityShadowResponse(
        Long userId,
        Long accountId,
        Long deptId,
        Long tenantId,
        String userName,
        String nickName,
        Integer deptType,
        String deptCategory,
        String deptName,
        Long roleId,
        String roleKey,
        String roleName,
        String effectiveDataScope,
        List<Long> buildingIds,
        List<WorkIdentityDeptOptionResponse> gridNodes) {

    public static WorkIdentityShadowResponse from(WorkIdentityShadow shadow) {
        return new WorkIdentityShadowResponse(
                shadow.userId(),
                shadow.accountId(),
                shadow.deptId(),
                shadow.tenantId(),
                shadow.userName(),
                shadow.nickName(),
                shadow.deptType(),
                shadow.deptCategory(),
                shadow.deptName(),
                shadow.roleId(),
                shadow.roleKey(),
                shadow.roleName(),
                shadow.effectiveDataScope(),
                shadow.buildingIds(),
                shadow.gridNodes().stream()
                        .map(WorkIdentityDeptOptionResponse::from)
                        .toList());
    }
}
