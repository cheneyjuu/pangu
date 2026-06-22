package com.pangu.application.admin;

import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.dto.RoleQuery;
import com.pangu.domain.model.role.PermissionCatalog;
import com.pangu.domain.model.role.RoleListItem;
import com.pangu.domain.model.role.RolePermissionDetail;
import com.pangu.domain.repository.SysPermissionRepository;
import com.pangu.domain.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色管理读侧查询服务（M4-1，读写分离）。
 *
 * <p>与写侧 {@link RoleAdminApplicationService} 分治：本服务纯只读，无 {@code @Transactional}，
 * 范式同 {@code OwnerQueryService}。供 {@code RoleAdminController}（角色列表 + 某角色权限明细）
 * 与 {@code PermissionAdminController}（全量权限清单）共用。
 *
 * <p>权限收口：三个调用点统一要求 {@code admin:role:read}（V1.4 已授 GOV_SUPER_ADMIN）。
 * {@code sys_role} / {@code sys_permission} 平台级表，不挂 {@code @DataScope}。
 */
@Service
@RequiredArgsConstructor
public class RoleAdminQueryService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;

    /**
     * 角色分页列表（带每行已授 permission 数）。
     */
    public Page<RoleListItem> pageRoles(RoleQuery query) {
        return roleRepository.pageRoles(query);
    }

    /**
     * 某角色已授权限明细。角色不存在抛 {@link RoleAdminApplicationException}（ROLE_NOT_FOUND），
     * 复用写侧异常体系 → web 层 {@code 404}。
     */
    public List<RolePermissionDetail> listPermissionsByRole(Long roleId) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleAdminApplicationException(
                        RoleAdminApplicationException.Reason.ROLE_NOT_FOUND,
                        "角色不存在：role_id=" + roleId));
        return roleRepository.listPermissionsByRole(roleId);
    }

    /**
     * 平台全量可授权限清单（授权页勾选用）。
     */
    public List<PermissionCatalog> listAllPermissions() {
        return permissionRepository.listAll();
    }
}
