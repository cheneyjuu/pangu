package com.pangu.application.admin;

import com.pangu.application.admin.command.AssignPermissionCommand;
import com.pangu.application.admin.command.CreateRoleCommand;
import com.pangu.domain.model.role.SysRole;
import com.pangu.domain.repository.SysRolePermissionRepository;
import com.pangu.domain.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * SaaS 管理员动态角色管理编排服务（M2-4）。
 *
 * <p>编排三条 use case：
 * <ol>
 *   <li>{@link #createRole} 新建非系统角色；</li>
 *   <li>{@link #assignPermission} 给已有角色授予一项 permission；</li>
 *   <li>{@link #deleteRole} 删除非系统角色（trigger 7 兜底拒绝预置角色）。</li>
 * </ol>
 *
 * <p>红线策略：本服务不暴露 {@code update} —— 角色的端归属 / fixed_data_scope 一旦
 * 落库就不允许在线变更（涉及历史数据 effective_data_scope 一致性）。需要变更者由
 * 运维通过 Flyway 迁移完成。
 *
 * <p>本服务对外只抛 {@link RoleAdminApplicationException}；DB 触发器（trigger 6/7）
 * 与唯一约束在 repository 层翻译为端口异常，本服务再二次转译为业务原因码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAdminApplicationService {

    /** 端归属白名单：与 sys_role.allowed_dept_category 的 CHECK 约束保持一致。 */
    private static final Set<String> ALLOWED_DEPT_CATEGORIES = Set.of("G", "B", "S");

    /** data_scope 白名单：与 sys_role.fixed_data_scope / default_data_scope 的 CHECK 约束保持一致。 */
    private static final Set<String> ALLOWED_DATA_SCOPES = Set.of("ALL_COMMUNITY", "OWNER_GROUP", "ORG_ONLY");

    private final SysRoleRepository roleRepository;
    private final SysRolePermissionRepository rolePermissionRepository;

    /**
     * 新建非系统角色。
     *
     * <p>校验顺序：
     * <ol>
     *   <li>应用层入参校验（必填 + 取值范围）；</li>
     *   <li>roleKey UNIQUE 由 DB 唯一约束兜底（{@link com.pangu.domain.repository.SysRoleRepository.DuplicateRoleKeyException}）；</li>
     *   <li>fixed_data_scope 与 default_data_scope 的一致性由 sys_role 的 chk_role_scope_consistency CHECK 兜底。</li>
     * </ol>
     */
    @Transactional
    public SysRole createRole(CreateRoleCommand cmd) {
        validate(cmd);
        SysRole role = SysRole.forCreate(
                cmd.roleKey(),
                cmd.roleName(),
                cmd.allowedDeptCategory(),
                cmd.fixedDataScope(),
                cmd.defaultDataScope());
        try {
            return roleRepository.insert(role);
        } catch (SysRoleRepository.DuplicateRoleKeyException e) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_KEY_DUPLICATE,
                    "role_key 已存在：" + cmd.roleKey(), e);
        }
    }

    /**
     * 给指定角色授予一项 permission。
     *
     * <p>不做应用层"端归属一致 + redline 必有 fixed_data_scope"的二次校验 ——
     * 全部沉到 trigger 6，避免应用层 DB 双校验导致的语义飘移。
     */
    @Transactional
    public void assignPermission(AssignPermissionCommand cmd) {
        if (cmd.roleId() == null || cmd.permissionKey() == null || cmd.permissionKey().isBlank()) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "roleId 与 permissionKey 必填");
        }
        // 先确认角色存在，给前端一个明确的 ROLE_NOT_FOUND，否则 trigger 6 会笼统报"角色不存在"
        if (roleRepository.findById(cmd.roleId()).isEmpty()) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_NOT_FOUND,
                    "角色不存在：role_id=" + cmd.roleId());
        }
        try {
            rolePermissionRepository.assign(cmd.roleId(), cmd.permissionKey(), cmd.grantedBy());
        } catch (SysRolePermissionRepository.DuplicateAssignmentException e) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.PERMISSION_ALREADY_ASSIGNED,
                    "已存在 (role_id=" + cmd.roleId() + ", permission_key=" + cmd.permissionKey() + ") 授予记录", e);
        } catch (SysRolePermissionRepository.AssignmentConsistencyException e) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.PERMISSION_ASSIGNMENT_INCONSISTENT,
                    "permission 授予一致性失败（端归属 / 红线 / 外键）：" + e.getMessage(), e);
        }
    }

    /**
     * 撤销指定角色的一项 permission（写侧 revoke，对称于 {@link #assignPermission}）。
     *
     * <p>校验顺序与 assignPermission 一致：先确认角色存在（ROLE_NOT_FOUND），
     * 再调 repository.revoke；revoke 返回 0（授予记录本不存在）翻译为
     * {@link RoleAdminApplicationException.Reason#PERMISSION_NOT_ASSIGNED}。
     */
    @Transactional
    public void revokePermission(Long roleId, String permissionKey) {
        if (roleId == null || permissionKey == null || permissionKey.isBlank()) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "roleId 与 permissionKey 必填");
        }
        if (roleRepository.findById(roleId).isEmpty()) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_NOT_FOUND,
                    "角色不存在：role_id=" + roleId);
        }
        int affected = rolePermissionRepository.revoke(roleId, permissionKey);
        if (affected == 0) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.PERMISSION_NOT_ASSIGNED,
                    "permission 未授予该角色，无需撤销：role_id=" + roleId
                            + ", permission_key=" + permissionKey);
        }
    }

    /**
     * 删除非系统角色。trigger 7 拒绝 is_system=1 的角色，应用层翻译为
     * {@link RoleAdminApplicationException.Reason#ROLE_PROTECTED}。
     *
     * <p>与 trigger 7 的语义对齐：保留预置 13 个 role 不可被 SaaS 管理员或后台脚本删除，
     * 防止误删导致 effective_data_scope 校验链断裂。
     */
    @Transactional
    public void deleteRole(Long roleId) {
        if (roleId == null) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "roleId 必填");
        }
        try {
            int affected = roleRepository.delete(roleId);
            if (affected == 0) {
                throw new RoleAdminApplicationException(
                        RoleAdminApplicationException.Reason.ROLE_NOT_FOUND,
                        "角色不存在：role_id=" + roleId);
            }
        } catch (SysRoleRepository.SystemRoleProtectedException e) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PROTECTED,
                    "is_system=1 的预置角色禁止删除", e);
        }
    }

    private void validate(CreateRoleCommand cmd) {
        if (cmd.roleKey() == null || cmd.roleKey().isBlank()
                || cmd.roleName() == null || cmd.roleName().isBlank()) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "roleKey / roleName 必填");
        }
        if (!ALLOWED_DEPT_CATEGORIES.contains(cmd.allowedDeptCategory())) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "allowedDeptCategory 取值必须为 G/B/S，实际：" + cmd.allowedDeptCategory());
        }
        if (!ALLOWED_DATA_SCOPES.contains(cmd.defaultDataScope())) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "defaultDataScope 取值必须为 ALL_COMMUNITY/OWNER_GROUP/ORG_ONLY，实际：" + cmd.defaultDataScope());
        }
        if (cmd.fixedDataScope() != null && !ALLOWED_DATA_SCOPES.contains(cmd.fixedDataScope())) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "fixedDataScope 取值必须为 ALL_COMMUNITY/OWNER_GROUP/ORG_ONLY 或 null，实际：" + cmd.fixedDataScope());
        }
        if (cmd.fixedDataScope() != null && !cmd.fixedDataScope().equals(cmd.defaultDataScope())) {
            throw new RoleAdminApplicationException(
                    RoleAdminApplicationException.Reason.ROLE_PARAM_INVALID,
                    "fixedDataScope 不为 null 时必须等于 defaultDataScope");
        }
    }
}
