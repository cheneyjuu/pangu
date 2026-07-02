package com.pangu.domain.model.user;

import java.util.List;

/**
 * 管理端工作身份 / 分身视图。
 *
 * <p>一个自然人账号可拥有多个 {@code sys_user} 工作身份；每个工作身份只绑定一个
 * RBAC 角色，OWNER_GROUP 类角色的 ABAC 楼栋范围来自 {@code sys_user_building}。
 */
public record WorkIdentityShadow(
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
        List<Long> buildingIds) {

    public WorkIdentityShadow {
        buildingIds = buildingIds == null ? List.of() : List.copyOf(buildingIds);
    }
}
