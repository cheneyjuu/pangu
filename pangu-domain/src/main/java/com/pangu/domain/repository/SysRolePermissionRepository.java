package com.pangu.domain.repository;

/**
 * 角色 ↔ 能力点（{@code sys_role_permission}）仓储端口。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/SysRolePermissionRepositoryImpl}。
 *
 * <p>事务约定：DB 触发器 6（role.allowed_dept_category 必须出现在
 * permission.allowed_dept_categories；redline=1 要求 fixed_data_scope NOT NULL）
 * 在 BEFORE INSERT 阶段做兜底，应用层只翻译两类故障：
 * <ul>
 *   <li>主键冲突（同 (role_id, permission_key) 已授予）→ {@link DuplicateAssignmentException}；</li>
 *   <li>trigger 6 一致性失败 / 外键不存在 → {@link AssignmentConsistencyException}。</li>
 * </ul>
 */
public interface SysRolePermissionRepository {

    /** 授予 role 一项 permission；同 PK 重复授予抛 {@link DuplicateAssignmentException}。 */
    void assign(Long roleId, String permissionKey, Long grantedBy);

    /** 撤销 role 的一项 permission；返回实际删除行数。 */
    int revoke(Long roleId, String permissionKey);

    /** 统计 role 已授予的 permission 数；供 SaaS 后台展示。 */
    long countByRole(Long roleId);

    /** PK 冲突信号。 */
    class DuplicateAssignmentException extends RuntimeException {
        public DuplicateAssignmentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** trigger 6 一致性 / 外键失败信号。 */
    class AssignmentConsistencyException extends RuntimeException {
        public AssignmentConsistencyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
