package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.role.SysRole;

/**
 * 角色响应 DTO；将 domain {@link SysRole} 投影到 web 层契约。
 */
public record RoleResponse(
        Long roleId,
        String roleKey,
        String roleName,
        String allowedDeptCategory,
        String fixedDataScope,
        String defaultDataScope,
        int isSystem) {

    public static RoleResponse from(SysRole role) {
        return new RoleResponse(
                role.roleId(),
                role.roleKey(),
                role.roleName(),
                role.allowedDeptCategory(),
                role.fixedDataScope(),
                role.defaultDataScope(),
                role.isSystem());
    }
}
