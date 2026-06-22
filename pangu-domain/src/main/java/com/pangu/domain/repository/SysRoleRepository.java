package com.pangu.domain.repository;

import com.pangu.domain.common.Page;
import com.pangu.domain.gateway.dto.RoleQuery;
import com.pangu.domain.model.role.RoleListItem;
import com.pangu.domain.model.role.RolePermissionDetail;
import com.pangu.domain.model.role.SysRole;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓储端口（Hexagonal Port）— 服务 SaaS 管理员的角色 CRUD + 读侧查询。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/SysRoleRepositoryImpl}。
 *
 * <p>设计原则：
 * <ul>
 *   <li>写侧暴露 SaaS 管理员后台需要的接口（{@code findById} / {@code findByRoleKey} /
 *       {@code insert} / {@code delete} / {@code updateDefaultDataScope}）——
 *       {@code is_system} / {@code fixed_data_scope} 等结构性字段仍不可在线变更；
 *       仅 {@code default_data_scope} 允许在 fixed 为空时由 {@code updateDefaultDataScope} 修改。</li>
 *   <li>读侧（M4-1 补）{@code pageRoles} / {@code listPermissionsByRole} 为纯查询，不挂
 *       {@code @DataScope}——{@code sys_role} 平台级表无 tenant/dept/building 维度，
 *       收口由 endpoint {@code @PreAuthorize(admin:role:read)} 保证。</li>
 *   <li>{@code delete} 的 trigger 7（is_system=1 拒绝）由 DB 层兜底，本端口仅返回行影响数
 *       供应用层判定 not-found / silent-prevented。</li>
 * </ul>
 */
public interface SysRoleRepository {

    Optional<SysRole> findById(Long roleId);

    Optional<SysRole> findByRoleKey(String roleKey);

    /**
     * 角色分页列表（带每行已授 permission 数）。
     *
     * @param query 过滤 + 分页参数（roleKey/roleName 模糊、isSystem/status 过滤）
     * @return 当前页角色列表行 + 总数
     */
    Page<RoleListItem> pageRoles(RoleQuery query);

    /**
     * 某角色已授的权限明细（JOIN sys_permission）。
     *
     * <p>调用方应先 {@link #findById(Long)} 校验角色存在；不存在由应用层抛
     * {@code ROLE_NOT_FOUND}。本方法对不存在的 roleId 返回空列表（不报错）。
     *
     * @param roleId 角色 id
     * @return 权限明细列表，按 permission_group, permission_key 排序
     */
    List<RolePermissionDetail> listPermissionsByRole(Long roleId);

    /**
     * 新建角色（is_system=0）。返回带数据库主键的角色。
     *
     * @throws DuplicateRoleKeyException sys_role.role_key UNIQUE 约束冲突
     */
    SysRole insert(SysRole role);

    /**
     * 删除角色（仅允许 is_system=0）。
     *
     * <p>trigger 7 在 BEFORE DELETE 阶段拦截 is_system=1 的预置角色并抛 SQLException；
     * 应用层捕获 {@link SystemRoleProtectedException} 转译为 {@code 403 ROLE_PROTECTED}。
     *
     * <p>{@code role_id} 不存在时返回 0；存在但被 trigger 7 拦截时抛
     * {@link SystemRoleProtectedException}。
     *
     * @return 实际删除的行数（0 或 1）
     */
    int delete(Long roleId);

    /**
     * 在线变更角色的 {@code default_data_scope}（M4-1 数据范围写回）。
     *
     * <p><b>红线</b>：{@code fixed_data_scope} 非空的角色其数据范围被法理锁死，
     * 应用层应先拦截并抛 {@code ROLE_SCOPE_LOCKED}，不得调用本方法——
     * 否则 {@code chk_role_scope_consistency} CHECK 会兜底拒绝（fixed 非空时
     * default 必须等于 fixed）。
     *
     * <p>本端口只做裸 UPDATE，不判断 fixed；{@code role_id} 不存在返回 0。
     *
     * @return 实际更新行数（0 或 1）
     */
    int updateDefaultDataScope(Long roleId, String defaultDataScope);

    /** sys_role.role_key UNIQUE 约束冲突信号。 */
    class DuplicateRoleKeyException extends RuntimeException {
        public DuplicateRoleKeyException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** trigger 7 拒绝删除 is_system=1 角色的信号。 */
    class SystemRoleProtectedException extends RuntimeException {
        public SystemRoleProtectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
