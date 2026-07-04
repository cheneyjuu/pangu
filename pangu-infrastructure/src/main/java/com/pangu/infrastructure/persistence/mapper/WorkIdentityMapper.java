package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理端工作身份授权 Mapper。
 */
@Mapper
public interface WorkIdentityMapper {

    List<AccountCandidateRow> searchAccountCandidates(@Param("keyword") String keyword,
                                                      @Param("limit") int limit);

    List<AccountCandidateRow> searchAccountCandidatesByRole(@Param("keyword") String keyword,
                                                            @Param("roleKey") String roleKey,
                                                            @Param("limit") int limit);

    List<AccountCandidateRow> listAccountNameSearchPool(@Param("limit") int limit);

    List<AccountCandidateRow> listAccountNameSearchPoolByRole(@Param("roleKey") String roleKey,
                                                              @Param("limit") int limit);

    AccountCandidateRow selectAccount(@Param("accountId") Long accountId);

    AccountCandidateRow selectAccountByPhone(@Param("phone") String phone);

    List<ShadowRow> selectShadowsByAccount(@Param("accountId") Long accountId);

    ShadowRow selectShadow(@Param("accountId") Long accountId,
                           @Param("userId") Long userId);

    ShadowRow selectShadowByUserId(@Param("userId") Long userId);

    List<Long> selectActiveBuildingIds(@Param("userId") Long userId);

    List<BuildingScopeRow> selectDeptBuildingScopes(@Param("deptId") Long deptId);

    List<DeptOptionRow> selectDeptOptions(@Param("deptCategory") String deptCategory,
                                          @Param("tenantId") Long tenantId);

    List<DeptOptionRow> selectGridChildren(@Param("communityDeptId") Long communityDeptId);

    List<DeptOptionRow> selectAssignedGridDepts(@Param("userId") Long userId);

    List<BuildingScopeRow> selectDistinctBuildings(@Param("tenantId") Long tenantId);

    List<BuildingScopeRow> selectDistinctBuildingsByTenants(@Param("tenantIds") List<Long> tenantIds);

    List<Long> selectCommunityTenantScope(@Param("communityDeptId") Long communityDeptId);

    DeptOptionRow selectDept(@Param("deptId") Long deptId);

    boolean existsAccountDept(@Param("accountId") Long accountId,
                              @Param("deptId") Long deptId);

    long countActiveUsersByDept(@Param("deptId") Long deptId);

    int deactivateDeptBuildingScope(@Param("deptId") Long deptId);

    int upsertDeptBuildingScope(@Param("deptId") Long deptId,
                                @Param("tenantId") Long tenantId,
                                @Param("buildingId") Long buildingId,
                                @Param("assignedBy") Long assignedBy);

    int deactivateUserGridDeptAssignments(@Param("userId") Long userId);

    int upsertUserGridDeptAssignment(@Param("userId") Long userId,
                                     @Param("gridDeptId") Long gridDeptId,
                                     @Param("assignedBy") Long assignedBy);

    int insertAccount(AccountInsertRow row);

    int updateAccountLastActiveIdentity(@Param("accountId") Long accountId,
                                        @Param("identityId") Long identityId,
                                        @Param("identityType") String identityType);

    int insertSysUser(SysUserInsertRow row);

    int insertGridDept(GridDeptInsertRow row);

    int updateGridDeptName(@Param("deptId") Long deptId,
                           @Param("deptName") String deptName);

    int deactivateGridDept(@Param("deptId") Long deptId);

    int insertSysUserRole(@Param("userId") Long userId,
                          @Param("roleId") Long roleId,
                          @Param("effectiveDataScope") String effectiveDataScope,
                          @Param("grantedBy") Long grantedBy);

    @Data
    class AccountCandidateRow {
        private Long accountId;
        private String phone;
        private String realNameCipher;
        private Integer realNameVerified;
        private Integer status;
    }

    @Data
    class AccountInsertRow {
        private Long accountId;
        private String phone;
        private String realName;
        private Integer realNameVerified;
    }

    @Data
    class ShadowRow {
        private Long userId;
        private Long accountId;
        private Long deptId;
        private Long tenantId;
        private String userName;
        private String nickName;
        private Integer deptType;
        private String deptCategory;
        private String deptName;
        private Long roleId;
        private String roleKey;
        private String roleName;
        private String effectiveDataScope;
    }

    @Data
    class DeptOptionRow {
        private Long deptId;
        private Long parentId;
        private String ancestors;
        private String deptName;
        private Integer deptType;
        private String deptCategory;
        private Long tenantId;
    }

    @Data
    class BuildingScopeRow {
        private Long tenantId;
        private Long buildingId;
    }

    @Data
    class SysUserInsertRow {
        private Long userId;
        private Long accountId;
        private Long deptId;
        private String userName;
        private String nickName;
    }

    @Data
    class GridDeptInsertRow {
        private Long deptId;
        private Long parentId;
        private String ancestors;
        private String deptName;
        private Long tenantId;
        private Integer orderNum;
    }
}
