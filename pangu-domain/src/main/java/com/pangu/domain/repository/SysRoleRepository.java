package com.pangu.domain.repository;

import com.pangu.domain.model.role.SysRole;

import java.util.Optional;

/**
 * 角色仓储端口（Hexagonal Port）— 服务 SaaS 管理员的角色 CRUD。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/SysRoleRepositoryImpl}。
 *
 * <p>设计原则：
 * <ul>
 *   <li>只暴露 SaaS 管理员后台需要的 4 条接口（{@code findById} / {@code findByRoleKey} /
 *       {@code insert} / {@code delete}）—— 不暴露 UPDATE，避免把 is_system / fixed_data_scope
 *       等结构性字段暴露给后台动态修改；这些字段一旦预置就不能在线变更。</li>
 *   <li>{@code delete} 的 trigger 7（is_system=1 拒绝）由 DB 层兜底，本端口仅返回行影响数
 *       供应用层判定 not-found / silent-prevented。</li>
 * </ul>
 */
public interface SysRoleRepository {

    Optional<SysRole> findById(Long roleId);

    Optional<SysRole> findByRoleKey(String roleKey);

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
