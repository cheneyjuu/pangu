// 关联业务：查询登录工作身份的租户、组织、权限和物业管理模式上下文。
package com.pangu.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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
     * 读取当前自然人账号已登记实名信息原值，由上层容错解密并只用于实名刷脸发起。
     */
    FaceAuthIdentityRow loadFaceAuthIdentity(@Param("accountId") Long accountId);

    /**
     * 将当前业主身份升级到指定认证等级。
     */
    int upgradeCUserAuthLevel(@Param("uid") Long uid,
                              @Param("accountId") Long accountId,
                              @Param("authLevel") int authLevel);

    /**
     * 人脸核身通过后同步标记自然人实名状态。
     */
    int markAccountRealNameVerified(@Param("accountId") Long accountId);

    /**
     * 记录业主端 L3 刷脸核身凭证审计，不存储人脸图像。
     */
    int insertFaceAuthAttestation(@Param("uid") Long uid,
                                  @Param("accountId") Long accountId,
                                  @Param("provider") String provider,
                                  @Param("providerRequestId") String providerRequestId,
                                  @Param("providerResult") String providerResult,
                                  @Param("verified") int verified,
                                  @Param("authLevelAfter") int authLevelAfter);

    /**
     * 反查业主名下任一房产的 tenant_id——C_USER 登录时 JWT 未带 tenantId
     * 时用此作默认值。返回 null 表示该业主未关联任何房产（应拒绝登录）。
     */
    Long selectDefaultTenantByUid(@Param("uid") Long uid);

    /**
     * 街道办等跨租户 G 端根节点登录管理端时，若前端尚未显式选择小区，
     * 取其组织树下第一个可管辖 tenant 作为本次会话的默认小区上下文。
     */
    Long selectDefaultTenantByGovernmentDept(@Param("deptId") Long deptId);

    /**
     * 查询街镇或平台根组织当前可切换监管的已启用小区。
     */
    List<GovernmentManagedCommunityRow> selectManagedCommunitiesByGovernmentDept(
            @Param("deptId") Long deptId);

    /**
     * 校验目标小区是否仍在街镇或平台根组织的有效监管范围内。
     */
    boolean existsManagedCommunityByGovernmentDept(@Param("deptId") Long deptId,
                                                   @Param("tenantId") Long tenantId);

    /**
     * 给定 role_id 反查其所有 permission_key。
     */
    List<String> selectPermissionsByRoleId(@Param("roleId") Long roleId);

    /**
     * 给定 sys_user.user_id 反查其授权的 building_id 列表（仅 status=1 的活跃记录）。
     */
    List<Long> selectAuthorizedBuildingIds(@Param("userId") Long userId);

    /**
     * 给定 sys_user.user_id 反查其授权的 tenant/building 范围（仅 status=1 的活跃记录）。
     */
    List<AuthorizedBuildingScopeRow> selectAuthorizedBuildingScopes(@Param("userId") Long userId);

    class AuthorizedBuildingScopeRow {
        private Long tenantId;
        private Long buildingId;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public Long getBuildingId() { return buildingId; }
        public void setBuildingId(Long buildingId) { this.buildingId = buildingId; }
    }

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

    /**
     * 政府组织可监管小区的扁平摘要，供会话上下文切换与左上角小区选择器共同使用。
     */
    class GovernmentManagedCommunityRow {
        private Long tenantId;
        private String tenantName;
        private Integer plannedHouseholdCount;
        private BigDecimal totalExclusiveArea;
        private String governanceStatus;
        private String propertyMode;

        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }
        public Integer getPlannedHouseholdCount() { return plannedHouseholdCount; }
        public void setPlannedHouseholdCount(Integer plannedHouseholdCount) {
            this.plannedHouseholdCount = plannedHouseholdCount;
        }
        public BigDecimal getTotalExclusiveArea() { return totalExclusiveArea; }
        public void setTotalExclusiveArea(BigDecimal totalExclusiveArea) {
            this.totalExclusiveArea = totalExclusiveArea;
        }
        public String getGovernanceStatus() { return governanceStatus; }
        public void setGovernanceStatus(String governanceStatus) { this.governanceStatus = governanceStatus; }
        public String getPropertyMode() { return propertyMode; }
        public void setPropertyMode(String propertyMode) { this.propertyMode = propertyMode; }
    }

    /**
     * 人脸核身发起前读取的自然人实名信息，字段保持密文或开发期 mock 原值。
     */
    class FaceAuthIdentityRow {
        private String realNameCipher;
        private String idCardCipher;

        public String getRealNameCipher() { return realNameCipher; }
        public void setRealNameCipher(String realNameCipher) { this.realNameCipher = realNameCipher; }
        public String getIdCardCipher() { return idCardCipher; }
        public void setIdCardCipher(String idCardCipher) { this.idCardCipher = idCardCipher; }
    }
}
