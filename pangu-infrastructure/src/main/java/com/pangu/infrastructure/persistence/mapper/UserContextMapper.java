package com.pangu.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 装载 {@link com.pangu.domain.context.UserContext} 所需的 RBAC 反查 mapper。
 *
 * <p>负责：sys_user / sys_user_role / sys_role / sys_role_permission /
 * sys_user_building / sys_dept / t_account / c_user 七表 JOIN，
 * 输出已经聚合的 row（{@link SysUserContextRow}）+ 业主活跃身份 row
 * （{@link CUserContextRow}）。
 */
@Mapper
public interface UserContextMapper {

    /**
     * 根据 sys_user.user_id 装配管理端身份上下文（含 dept / role / 楼栋）。
     *
     * @return 不存在或已禁用返回 null
     */
    SysUserContextRow loadSysUserContext(@Param("accountId") Long accountId,
                                         @Param("userId") Long userId);

    /**
     * 根据 c_user.uid 装配业主端身份上下文。
     */
    CUserContextRow loadCUserContext(@Param("accountId") Long accountId,
                                     @Param("uid") Long uid);

    /**
     * 反查业主名下任一房产的 tenant_id——C_USER 登录时 JWT 未带 tenantId
     * 时用此作默认值。返回 null 表示该业主未关联任何房产（应拒绝登录）。
     */
    Long selectDefaultTenantByUid(@Param("uid") Long uid);

    /**
     * 给定 role_id 反查其所有 permission_key。
     */
    List<String> selectPermissionsByRoleId(@Param("roleId") Long roleId);

    /**
     * 给定 sys_user.user_id 反查其授权的 building_id 列表（仅 status=1 的活跃记录）。
     */
    List<Long> selectAuthorizedBuildingIds(@Param("userId") Long userId);

    /**
     * 管理端身份装配后的扁平 row。
     */
    class SysUserContextRow {
        private Long userId;
        private Long accountId;
        private Long deptId;
        private Long deptTenantId;
        /** sys_dept.dept_category：'G' / 'B' / 'S'。 */
        private String deptCategory;
        /** sys_dept.dept_type：1-11，见 V1 schema 注释。 */
        private Integer deptType;
        private Long roleId;
        private String roleKey;
        /** sys_user_role.effective_data_scope 或 sys_role.fixed_data_scope。 */
        private String effectiveDataScope;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public Long getDeptId() { return deptId; }
        public void setDeptId(Long deptId) { this.deptId = deptId; }
        public Long getDeptTenantId() { return deptTenantId; }
        public void setDeptTenantId(Long deptTenantId) { this.deptTenantId = deptTenantId; }
        public String getDeptCategory() { return deptCategory; }
        public void setDeptCategory(String deptCategory) { this.deptCategory = deptCategory; }
        public Integer getDeptType() { return deptType; }
        public void setDeptType(Integer deptType) { this.deptType = deptType; }
        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
        public String getRoleKey() { return roleKey; }
        public void setRoleKey(String roleKey) { this.roleKey = roleKey; }
        public String getEffectiveDataScope() { return effectiveDataScope; }
        public void setEffectiveDataScope(String effectiveDataScope) { this.effectiveDataScope = effectiveDataScope; }
    }

    /**
     * 业主端身份装配后的扁平 row。
     */
    class CUserContextRow {
        private Long uid;
        private Long accountId;
        private Integer authLevel;

        public Long getUid() { return uid; }
        public void setUid(Long uid) { this.uid = uid; }
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public Integer getAuthLevel() { return authLevel; }
        public void setAuthLevel(Integer authLevel) { this.authLevel = authLevel; }
    }
}
