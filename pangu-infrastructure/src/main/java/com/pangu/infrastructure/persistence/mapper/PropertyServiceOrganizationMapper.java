// 关联业务：映射物业服务组织登记、材料、企业核验和项目部启用所需的持久化操作。
package com.pangu.infrastructure.persistence.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 物业服务组织登记 MyBatis Mapper。
 */
@Mapper
public interface PropertyServiceOrganizationMapper {

    List<OrganizationRow> selectOrganizations(@Param("tenantId") Long tenantId);

    OrganizationRow selectOrganization(@Param("tenantId") Long tenantId,
                                       @Param("organizationId") Long organizationId);

    OrganizationRow selectOrganizationForUpdate(@Param("tenantId") Long tenantId,
                                                @Param("organizationId") Long organizationId);

    OrganizationRow selectActiveOrganization(@Param("tenantId") Long tenantId);

    int insertOrganization(OrganizationRow row);

    int updateOrganization(@Param("row") OrganizationRow row,
                           @Param("expectedVersion") int expectedVersion);

    EnterpriseRow selectEnterpriseByUscc(@Param("unifiedSocialCreditCode") String unifiedSocialCreditCode);

    EnterpriseRow selectEnterpriseById(@Param("enterpriseId") Long enterpriseId);

    EnterpriseRow selectEnterpriseByUsccForUpdate(
            @Param("unifiedSocialCreditCode") String unifiedSocialCreditCode);

    int insertEnterprise(EnterpriseRow row);

    int updateEnterpriseDepartment(@Param("enterpriseId") Long enterpriseId,
                                   @Param("enterpriseDeptId") Long enterpriseDeptId);

    int insertEnterpriseDepartment(DeptInsertRow row);

    int insertProjectDepartment(DeptInsertRow row);

    int insertMaterial(MaterialRow row);

    MaterialRow selectMaterial(@Param("organizationId") Long organizationId,
                               @Param("materialId") Long materialId);

    List<MaterialRow> selectMaterials(@Param("organizationId") Long organizationId);

    int deactivateMaterial(@Param("organizationId") Long organizationId,
                           @Param("materialId") Long materialId);

    long countActiveMaterialsByType(@Param("organizationId") Long organizationId,
                                    @Param("materialType") String materialType);

    int insertVerification(VerificationRow row);

    List<VerificationRow> selectVerifications(@Param("organizationId") Long organizationId);

    int insertAudit(@Param("organizationId") Long organizationId,
                    @Param("actorAccountId") Long actorAccountId,
                    @Param("actorUserId") Long actorUserId,
                    @Param("actorDeptId") Long actorDeptId,
                    @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus,
                    @Param("toStatus") String toStatus,
                    @Param("payloadJson") String payloadJson);

    @Data
    class EnterpriseRow {
        private Long enterpriseId;
        private Long enterpriseDeptId;
        private String legalName;
        private String unifiedSocialCreditCode;
        private Instant createTime;
        private Instant updateTime;
    }

    @Data
    class OrganizationRow {
        private Long organizationId;
        private Long tenantId;
        private Long enterpriseId;
        private Long projectDeptId;
        private String projectDeptName;
        private String serviceContactName;
        private String serviceContactPhone;
        private String serviceBasis;
        private LocalDate serviceStartDate;
        private LocalDate serviceEndDate;
        private String status;
        private Long submittedByAccountId;
        private Long submittedByUserId;
        private Instant submittedAt;
        private Long verifiedByAccountId;
        private Long verifiedByUserId;
        private Instant verifiedAt;
        private String rejectionReason;
        private Integer version;
        private Instant createTime;
        private Instant updateTime;
    }

    @Data
    class MaterialRow {
        private Long materialId;
        private Long organizationId;
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
    class VerificationRow {
        private Long verificationId;
        private Long organizationId;
        private String legalNameSnapshot;
        private String unifiedSocialCreditCodeSnapshot;
        private String verificationMethod;
        private String providerCode;
        private String sourceCode;
        private String providerRequestId;
        private String providerResultCode;
        private String verificationResult;
        private String businessStatus;
        private String resultMessage;
        private String inconsistentFieldsJson;
        private String evidenceReference;
        private String remark;
        private Long operatorAccountId;
        private Long operatorUserId;
        private String operatorRoleKey;
        private Boolean simulated;
        private Instant verifiedAt;
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
}
