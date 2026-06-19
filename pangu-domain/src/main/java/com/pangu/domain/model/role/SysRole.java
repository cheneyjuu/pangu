package com.pangu.domain.model.role;

/**
 * 角色值对象（domain 层 row-translation 友好型 record）。
 *
 * <p>区别于 {@code GovernanceLock} 这类有状态机的聚合根，{@link SysRole} 仅承载
 * 角色的 6 个核心配置字段，由 SaaS 管理员通过 {@code RoleAdminApplicationService}
 * 进行 CRUD。法理红线（trigger 1 / 3 / 6 / 7）由 PostgreSQL 触发器在数据层兜底，
 * 应用层只做参数补全与幂等性约束。
 *
 * @param roleId            主键，新建时为 {@code null}
 * @param roleKey           业务键（唯一，sys_role.role_key UNIQUE）
 * @param roleName          展示名
 * @param allowedDeptCategory 端归属：'G' 政务 / 'B' 业主 / 'S' 服务商
 * @param fixedDataScope    法理红线锁死的 effective_data_scope；可空
 * @param defaultDataScope  默认 effective_data_scope；不可空
 * @param isSystem          1=预置（trigger 7 拒绝删除），SaaS 管理员新建一律为 0
 */
public record SysRole(
        Long roleId,
        String roleKey,
        String roleName,
        String allowedDeptCategory,
        String fixedDataScope,
        String defaultDataScope,
        int isSystem) {

    /** SaaS 管理员新建角色的默认 isSystem=0；db 主键留 {@code null} 由 BIGSERIAL 生成。 */
    public static SysRole forCreate(String roleKey, String roleName, String allowedDeptCategory,
                                    String fixedDataScope, String defaultDataScope) {
        return new SysRole(null, roleKey, roleName, allowedDeptCategory,
                fixedDataScope, defaultDataScope, 0);
    }

    public SysRole withId(Long roleId) {
        return new SysRole(roleId, roleKey, roleName, allowedDeptCategory,
                fixedDataScope, defaultDataScope, isSystem);
    }
}
