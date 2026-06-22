package com.pangu.domain.model.role;

/**
 * 平台全量权限清单项（读侧视图，framework-light record）。
 *
 * <p>供管理台授权页勾选用——列出 {@code sys_permission} 全部可授权限的元信息，
 * 不含授予审计（那是 {@link RolePermissionDetail} 的事）。按
 * {@code permission_group, permission_key} 排序返回。
 *
 * @param permissionKey         权限业务键
 * @param description           权限描述
 * @param permissionGroup       权限组
 * @param allowedDeptCategories 端归属位组合
 * @param isLegalRedline        1=法理红线
 */
public record PermissionCatalog(
        String permissionKey,
        String description,
        String permissionGroup,
        String allowedDeptCategories,
        Integer isLegalRedline) {
}
