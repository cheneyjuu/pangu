package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.role.RoleListItem;

import java.time.Instant;

/**
 * 角色列表行响应 DTO（M4-1 读侧）。
 *
 * <p>比 {@link RoleResponse}（写侧 create 用）多 {@code status / permissionCount / createTime}，
 * 供管理台角色分页列表展示。
 */
public record RoleListItemResponse(
        Long roleId,
        String roleKey,
        String roleName,
        String allowedDeptCategory,
        String fixedDataScope,
        String defaultDataScope,
        int isSystem,
        String status,
        long permissionCount,
        Instant createTime) {

    public static RoleListItemResponse from(RoleListItem item) {
        return new RoleListItemResponse(
                item.roleId(),
                item.roleKey(),
                item.roleName(),
                item.allowedDeptCategory(),
                item.fixedDataScope(),
                item.defaultDataScope(),
                item.isSystem(),
                item.status(),
                item.permissionCount(),
                item.createTime());
    }
}
