package com.pangu.interfaces.web.controller;

import com.pangu.application.admin.RoleAdminQueryService;
import com.pangu.domain.model.role.PermissionCatalog;
import com.pangu.interfaces.web.controller.dto.admin.PermissionCatalogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台权限清单只读入口（M4-1 读侧）。
 *
 * <p>独立 controller 挂 {@code /api/v1/admin/permissions}，与
 * {@code RoleAdminController} 的 {@code /api/v1/admin/roles/{roleId}/permissions}
 * 路径变量彻底解耦，避开 {@code {roleId}} 字面量歧义。共用
 * {@link RoleAdminQueryService}。
 *
 * <p>鉴权 {@code admin:role:read}（V1.4 已授 GOV_SUPER_ADMIN）。供管理台授权页
 * 勾选可授权限——列出 {@code sys_permission} 全量元信息，按 group/key 排序。
 */
@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
public class PermissionAdminController extends BaseController {

    private final RoleAdminQueryService roleAdminQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:role:read')")
    public Result<List<PermissionCatalogResponse>> list() {
        List<PermissionCatalog> catalog = roleAdminQueryService.listAllPermissions();
        return success(catalog.stream().map(PermissionCatalogResponse::from).toList());
    }
}
