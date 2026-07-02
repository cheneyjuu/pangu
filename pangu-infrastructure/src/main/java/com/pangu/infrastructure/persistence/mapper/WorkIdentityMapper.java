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

    List<AccountCandidateRow> listAccountNameSearchPool(@Param("limit") int limit);

    AccountCandidateRow selectAccount(@Param("accountId") Long accountId);

    List<ShadowRow> selectShadowsByAccount(@Param("accountId") Long accountId);

    ShadowRow selectShadow(@Param("accountId") Long accountId,
                           @Param("userId") Long userId);

    List<Long> selectActiveBuildingIds(@Param("userId") Long userId);

    List<Long> selectDeptBuildingScopeIds(@Param("deptId") Long deptId);

    List<DeptOptionRow> selectDeptOptions(@Param("deptCategory") String deptCategory,
                                          @Param("tenantId") Long tenantId);

    List<DeptOptionRow> selectGridChildren(@Param("communityDeptId") Long communityDeptId);

    List<Long> selectDistinctBuildings(@Param("tenantId") Long tenantId);

    DeptOptionRow selectDept(@Param("deptId") Long deptId);

    boolean existsAccountDept(@Param("accountId") Long accountId,
                              @Param("deptId") Long deptId);

    int deactivateDeptBuildingScope(@Param("deptId") Long deptId);

    int upsertDeptBuildingScope(@Param("deptId") Long deptId,
                                @Param("buildingId") Long buildingId,
                                @Param("assignedBy") Long assignedBy);

    int insertSysUser(SysUserInsertRow row);

    int insertGridDept(GridDeptInsertRow row);

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
