package com.pangu.domain.context;

import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;

import java.util.Set;

/**
 * 跨层用户上下文（不可变值对象）。
 *
 * <p>M1 RBAC 重构后的终态：以自然人 {@code account_id} 为审计主键，
 * 通过 {@code identityType} + {@code activeIdentityId} 区分管理端工作账号 / C 端业主身份。
 *
 * <p>application/domain 层通过 {@link UserContextHolder} 获取当前线程的实例，
 * 由 infrastructure 层在 JWT 解析阶段装配并写入 ThreadLocal。
 *
 * @param accountId            自然人主体 ID（{@code t_account.account_id}，JWT sub）
 * @param identityType         {@link IdentityType#SYS_USER} 或 {@link IdentityType#C_USER}
 * @param activeIdentityId     当前活跃身份 ID：{@code sys_user.user_id} 或 {@code c_user.uid}
 * @param tenantId             当前租户 ID；街道办用户跨租户俯瞰时为 null
 * @param deptId               所属部门 ID（C 端为 null）
 * @param deptCategory         所属部门端归属：{@link DeptCategory#G}/{@link DeptCategory#B}/{@link DeptCategory#S}（C 端为 null）
 * @param deptType             所属部门类型（C 端为 null）；对齐 {@code sys_dept.dept_type}
 * @param dataScopeType        行级数据权限范围（{@link DataScopeType#ALL_COMMUNITY}/{@link DataScopeType#OWNER_GROUP}/{@link DataScopeType#ORG_ONLY}）
 * @param authLevel            认证等级（C 端 L1/L3 实名状态；管理端通常 L1）
 * @param roleKey              角色 key；C 端为 null
 * @param permissions          能力点 key 集合；@PreAuthorize 取此集合
 * @param authorizedBuildingIds OWNER_GROUP 时由 {@code sys_user_building} 反查得到的楼栋集合；其它 scope 为 emptySet
 */
public record UserContext(
        Long accountId,
        IdentityType identityType,
        Long activeIdentityId,
        Long tenantId,
        Long deptId,
        DeptCategory deptCategory,
        Integer deptType,
        DataScopeType dataScopeType,
        AuthenticationLevel authLevel,
        String roleKey,
        Set<String> permissions,
        Set<Long> authorizedBuildingIds
) {
    public UserContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        authorizedBuildingIds = authorizedBuildingIds == null ? Set.of() : Set.copyOf(authorizedBuildingIds);
    }

    /** 身份类型（SYS_USER 管理端工作账号 / C_USER 业主自然人）。 */
    public enum IdentityType {
        SYS_USER,
        C_USER
    }

    /** 三端归属枚举。 */
    public enum DeptCategory {
        /** 政务监管端 */
        G,
        /** 业主自治端 */
        B,
        /** 服务供应端 */
        S
    }

    /** {@link IdentityType#SYS_USER} 时返回 user_id；否则 null。 */
    public Long userId() {
        return identityType == IdentityType.SYS_USER ? activeIdentityId : null;
    }

    /** {@link IdentityType#C_USER} 时返回 uid；否则 null。 */
    public Long uid() {
        return identityType == IdentityType.C_USER ? activeIdentityId : null;
    }

    /** 是否拥有指定 permission_key。 */
    public boolean hasPermission(String permissionKey) {
        return permissions.contains(permissionKey);
    }

    /** 兼容旧业务调用：用于业务校验是否需要落兜底字段。 */
    public boolean isSysUser() {
        return identityType == IdentityType.SYS_USER;
    }

    public boolean isCUser() {
        return identityType == IdentityType.C_USER;
    }
}
