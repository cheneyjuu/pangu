package com.pangu.interfaces.web.controller;

import com.pangu.application.admin.RoleAdminApplicationException;
import com.pangu.application.admin.RoleAdminApplicationService;
import com.pangu.application.admin.RoleAdminQueryService;
import com.pangu.application.admin.command.AssignPermissionCommand;
import com.pangu.application.admin.command.CreateRoleCommand;
import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.dto.RoleQuery;
import com.pangu.domain.model.role.RoleListItem;
import com.pangu.domain.model.role.RolePermissionDetail;
import com.pangu.domain.model.role.SysRole;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.PageResponse;
import com.pangu.interfaces.web.controller.dto.admin.AssignPermissionRequest;
import com.pangu.interfaces.web.controller.dto.admin.CreateRoleRequest;
import com.pangu.interfaces.web.controller.dto.admin.RoleListItemResponse;
import com.pangu.interfaces.web.controller.dto.admin.RolePermissionResponse;
import com.pangu.interfaces.web.controller.dto.admin.RoleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SaaS 管理员动态角色管理 (M2-4 写侧 + M4-1 读侧) RESTful 入口。
 *
 * <p>API 路径：
 * <ul>
 *   <li>{@code POST   /api/v1/admin/roles}                              —— 新建非系统角色（写，{@code admin:role:manage}）；</li>
 *   <li>{@code POST   /api/v1/admin/roles/{roleId}/permissions}         —— 授予 permission（写，{@code admin:role:manage}）；</li>
 *   <li>{@code DELETE /api/v1/admin/roles/{roleId}/permissions/{permissionKey}} —— 撤销 permission（写，{@code admin:role:manage}）；</li>
 *   <li>{@code DELETE /api/v1/admin/roles/{roleId}}                     —— 删除非系统角色（写，{@code admin:role:manage}，trigger 7 拒绝预置）；</li>
 *   <li>{@code GET    /api/v1/admin/roles}                              —— 角色分页列表（读，{@code admin:role:read}，M4-1）；</li>
 *   <li>{@code GET    /api/v1/admin/roles/{roleId}/permissions}         —— 某角色已授权限明细（读，{@code admin:role:read}，M4-1）。</li>
 * </ul>
 *
 * <p>鉴权分治：写侧 {@code admin:role:manage}（V2.9 仅 GOV_SUPER_ADMIN）；读侧
 * {@code admin:role:read}（V1.4 已授 GOV_SUPER_ADMIN）。{@code sys_role} 平台级表，
 * 读侧不挂 {@code @DataScope}——收口由 endpoint {@code @PreAuthorize} 保证。
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleAdminController extends BaseController {

    /** 列表 page/size 上限，与 {@code SubjectAdminController.page()} / {@code OwnerController} 范式一致。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final RoleAdminApplicationService roleAdminApplicationService;
    private final RoleAdminQueryService roleAdminQueryService;

    @PostMapping
    @PreAuthorize("hasAuthority('admin:role:manage')")
    public ResponseEntity<Result<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        CreateRoleCommand cmd = new CreateRoleCommand(
                request.roleKey(),
                request.roleName(),
                request.allowedDeptCategory(),
                request.fixedDataScope(),
                request.defaultDataScope());
        SysRole created = roleAdminApplicationService.createRole(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("角色已创建", RoleResponse.from(created)));
    }

    @PostMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('admin:role:manage')")
    public Result<Void> assignPermission(@PathVariable("roleId") Long roleId,
                                          @Valid @RequestBody AssignPermissionRequest request) {
        AssignPermissionCommand cmd = new AssignPermissionCommand(
                roleId, request.permissionKey(), requireUserId());
        roleAdminApplicationService.assignPermission(cmd);
        return success("permission 已授予", null);
    }

    /**
     * 撤销角色的一项 permission（写侧 revoke，对称于 assignPermission）。
     *
     * <p>{@code permissionKey} 含冒号（如 {@code owner:list}），作为 path 段直接传——
     * Spring 默认不裁剪冒号，前端无需额外编码。角色不存在 → 404；授予记录不存在 → 404。
     */
    @DeleteMapping("/{roleId}/permissions/{permissionKey}")
    @PreAuthorize("hasAuthority('admin:role:manage')")
    public Result<Void> revokePermission(@PathVariable("roleId") Long roleId,
                                         @PathVariable("permissionKey") String permissionKey) {
        roleAdminApplicationService.revokePermission(roleId, permissionKey);
        return success("permission 已撤销", null);
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('admin:role:manage')")
    public Result<Void> deleteRole(@PathVariable("roleId") Long roleId) {
        roleAdminApplicationService.deleteRole(roleId);
        return success("角色已删除", null);
    }

    /**
     * 角色分页列表（M4-1 读侧）。
     *
     * <p>支持 {@code roleKey/roleName} 模糊、{@code isSystem/status} 过滤；每行带
     * {@code permissionCount}（子查询聚合）。预置角色（is_system=1）排序在前。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('admin:role:read')")
    public Result<PageResponse<RoleListItemResponse>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "roleKey", required = false) String roleKey,
            @RequestParam(name = "roleName", required = false) String roleName,
            @RequestParam(name = "isSystem", required = false) Integer isSystem,
            @RequestParam(name = "status", required = false) String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        RoleQuery query = new RoleQuery(roleKey, roleName, isSystem, status, safePage, safeSize);
        Page<RoleListItem> result = roleAdminQueryService.pageRoles(query);
        return success(PageResponse.from(result, RoleListItemResponse::from));
    }

    /**
     * 某角色已授权限明细（M4-1 读侧）。
     *
     * <p>角色不存在 → {@code 404 ROLE_NOT_FOUND}（复用写侧异常体系）。
     */
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('admin:role:read')")
    public Result<List<RolePermissionResponse>> permissions(@PathVariable("roleId") Long roleId) {
        List<RolePermissionDetail> details = roleAdminQueryService.listPermissionsByRole(roleId);
        return success(details.stream().map(RolePermissionResponse::from).toList());
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "未识别到登录用户，禁止访问该操作");
        }
        return userId;
    }
}
