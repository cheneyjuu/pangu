package com.pangu.interfaces.web.controller;

import com.pangu.application.admin.RoleAdminApplicationException;
import com.pangu.application.admin.RoleAdminApplicationService;
import com.pangu.application.admin.command.AssignPermissionCommand;
import com.pangu.application.admin.command.CreateRoleCommand;
import com.pangu.domain.model.role.SysRole;
import com.pangu.interfaces.security.SecurityUtils;
import com.pangu.interfaces.web.controller.dto.admin.AssignPermissionRequest;
import com.pangu.interfaces.web.controller.dto.admin.CreateRoleRequest;
import com.pangu.interfaces.web.controller.dto.admin.RoleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SaaS 管理员动态角色管理 (M2-4) RESTful 入口。
 *
 * <p>API 路径：
 * <ul>
 *   <li>{@code POST   /api/v1/admin/roles}                              —— 新建非系统角色；</li>
 *   <li>{@code POST   /api/v1/admin/roles/{roleId}/permissions}         —— 授予 permission；</li>
 *   <li>{@code DELETE /api/v1/admin/roles/{roleId}}                     —— 删除非系统角色（trigger 7 拒绝预置）。</li>
 * </ul>
 *
 * <p>鉴权：三个 endpoint 都要求 {@code admin:role:manage}（V2.9 仅授予 GOV_SUPER_ADMIN，
 * 与 GovernanceLockController.lock 共享同一管理 capability）。
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleAdminController extends BaseController {

    private final RoleAdminApplicationService roleAdminApplicationService;

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

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('admin:role:manage')")
    public Result<Void> deleteRole(@PathVariable("roleId") Long roleId) {
        roleAdminApplicationService.deleteRole(roleId);
        return success("角色已删除", null);
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
