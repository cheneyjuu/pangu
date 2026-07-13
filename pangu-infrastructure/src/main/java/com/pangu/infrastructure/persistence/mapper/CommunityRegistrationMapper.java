// 关联业务：映射小区注册申请、审核材料、审计和事务性租户开通数据。
package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 小区注册与冷启动 MyBatis Mapper。
 */
@Mapper
public interface CommunityRegistrationMapper {

    int insertApplication(ApplicationRow row);

    int updateApplication(@Param("row") ApplicationRow row, @Param("expectedVersion") int expectedVersion);

    ApplicationRow selectApplication(@Param("applicationId") Long applicationId);

    List<ApplicationRow> selectApplicationsByApplicant(@Param("applicantAccountId") Long applicantAccountId,
                                                       @Param("limit") int limit);

    List<ApplicationRow> selectApplicationsForReview(@Param("status") String status,
                                                     @Param("limit") int limit);

    boolean existsActiveFingerprint(@Param("fingerprint") String fingerprint,
                                    @Param("excludeApplicationId") Long excludeApplicationId);

    boolean existsProvisionedCommunity(@Param("fingerprint") String fingerprint,
                                       @Param("districtCode") String districtCode,
                                       @Param("communityName") String communityName,
                                       @Param("communityAddress") String communityAddress);

    int insertHousingTag(@Param("applicationId") Long applicationId, @Param("housingTag") String housingTag);

    int deleteHousingTags(@Param("applicationId") Long applicationId);

    List<String> selectHousingTags(@Param("applicationId") Long applicationId);

    int insertMaterial(MaterialRow row);

    MaterialRow selectMaterial(@Param("applicationId") Long applicationId,
                               @Param("materialId") Long materialId);

    List<MaterialRow> selectMaterials(@Param("applicationId") Long applicationId);

    int deactivateMaterial(@Param("applicationId") Long applicationId,
                           @Param("materialId") Long materialId,
                           @Param("applicantAccountId") Long applicantAccountId);

    long countActiveMaterials(@Param("applicationId") Long applicationId);

    int insertReview(ReviewRow row);

    List<ReviewRow> selectReviews(@Param("applicationId") Long applicationId);

    OnboardingRow selectOnboarding(@Param("applicationId") Long applicationId);

    int insertAudit(@Param("applicationId") Long applicationId,
                    @Param("actorAccountId") Long actorAccountId,
                    @Param("actorUserId") Long actorUserId,
                    @Param("actorDeptId") Long actorDeptId,
                    @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus,
                    @Param("toStatus") String toStatus,
                    @Param("payloadJson") String payloadJson);

    Long nextTenantId();

    DeptSourceRow selectDeptSource(@Param("deptId") Long deptId);

    Long selectDefaultGovernancePolicyId();

    int insertTenant(TenantProvisionRow row);

    int insertDept(DeptInsertRow row);

    RoleRow selectRoleByKey(@Param("roleKey") String roleKey);

    int insertSysUser(SysUserInsertRow row);

    int insertSysUserRole(@Param("userId") Long userId,
                          @Param("roleId") Long roleId,
                          @Param("effectiveDataScope") String effectiveDataScope,
                          @Param("grantedBy") Long grantedBy);

    int insertCommitteePosition(@Param("tenantId") Long tenantId,
                                @Param("userId") Long userId,
                                @Param("position") String position);

    Long selectExistingGovernmentUser(@Param("accountId") Long accountId,
                                      @Param("reviewerDeptId") Long reviewerDeptId);

    int insertOnboarding(OnboardingInsertRow row);

    @Data
    class ApplicationRow {
        private Long applicationId;
        private String applicationNo;
        private Long applicantAccountId;
        private String applicantName;
        private String applicantPhone;
        private String claimedIdentity;
        private String provinceCode;
        private String provinceName;
        private String cityCode;
        private String cityName;
        private String districtCode;
        private String districtName;
        private String communityName;
        private String communityAddress;
        private Integer declaredHouseholdCount;
        private String declaredPropertyMode;
        private String communityFingerprint;
        private String status;
        private String reviewMode;
        private Long reviewerAccountId;
        private Long reviewerUserId;
        private Long reviewerDeptId;
        private String reviewComment;
        private String fallbackReason;
        private Long provisionedTenantId;
        private Integer version;
        private Instant submittedAt;
        private Instant reviewedAt;
        private Instant createTime;
        private Instant updateTime;
    }

    @Data
    class MaterialRow {
        private Long materialId;
        private Long applicationId;
        private String materialType;
        private String objectKey;
        private String originalFileName;
        private String contentType;
        private Long fileSize;
        private String etag;
        private String sha256;
        private Long uploadedByAccountId;
        private String status;
        private Instant createTime;
    }

    @Data
    class ReviewRow {
        private Long reviewId;
        private Long applicationId;
        private String decision;
        private String reviewMode;
        private Long reviewerAccountId;
        private Long reviewerUserId;
        private Long reviewerDeptId;
        private String reviewComment;
        private String fallbackReason;
        private Instant createTime;
    }

    @Data
    class OnboardingRow {
        private Long onboardingId;
        private Long applicationId;
        private Long tenantId;
        private String status;
        private String officialAffiliationStatus;
        private String spaceLedgerStatus;
        private String propertyRosterStatus;
        private String denominatorStatus;
        private String ownerAccessQrStatus;
        private Long initializationDeptId;
        private Long committeeDeptId;
        private Long applicantWorkUserId;
        private Long createdByUserId;
        private Instant createTime;
        private Instant updateTime;
    }

    @Data
    class DeptSourceRow {
        private Long deptId;
        private String ancestors;
        private String deptName;
        private Integer deptType;
        private String deptCategory;
        private Long tenantId;
    }

    @Data
    class TenantProvisionRow {
        private Long tenantId;
        private String tenantCode;
        private String tenantShortCode;
        private String tenantName;
        private String propertyAreaName;
        private String propertyAreaCode;
        private String provinceCode;
        private String provinceName;
        private String cityCode;
        private String cityName;
        private String districtCode;
        private String districtName;
        private String streetName;
        private String address;
        private Integer plannedHouseholdCount;
        private Integer ownersAssemblyEstablished;
        private Integer committeeEstablished;
        private Long ruleConfigId;
        private String governanceStatus;
        private String registrationFingerprint;
        private String propertyMode;
        private Long registrationApplicationId;
        private String reviewMode;
        private Long reviewerUserId;
    }

    @Data
    class DeptInsertRow {
        private Long deptId;
        private Long parentId;
        private String ancestors;
        private String deptName;
        private Integer deptType;
        private String deptCategory;
        private Long tenantId;
        private Integer orderNum;
    }

    @Data
    class RoleRow {
        private Long roleId;
        private String roleKey;
        private String fixedDataScope;
        private String defaultDataScope;
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
    class OnboardingInsertRow {
        private Long onboardingId;
        private Long applicationId;
        private Long tenantId;
        private String officialAffiliationStatus;
        private Long initializationDeptId;
        private Long committeeDeptId;
        private Long applicantWorkUserId;
        private Long createdByUserId;
    }
}
