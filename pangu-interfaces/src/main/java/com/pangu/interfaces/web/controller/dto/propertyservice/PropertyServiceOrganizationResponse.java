// 关联业务：汇总物业服务组织登记、企业主体、材料与核验审计，供小区和属地管理端查看。
package com.pangu.interfaces.web.controller.dto.propertyservice;

import com.pangu.application.propertyservice.PropertyServiceOrganizationDetails;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganization;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 物业服务组织登记详情响应。
 */
public record PropertyServiceOrganizationResponse(
        Long organizationId,
        Long tenantId,
        Long enterpriseId,
        String legalName,
        String unifiedSocialCreditCode,
        Long projectDeptId,
        String projectDeptName,
        String serviceContactName,
        String serviceContactPhone,
        String serviceBasis,
        LocalDate serviceStartDate,
        LocalDate serviceEndDate,
        String status,
        String rejectionReason,
        int version,
        Instant submittedAt,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt,
        List<PropertyServiceOrganizationMaterialResponse> materials,
        List<VerificationResponse> verifications
) {
    public static PropertyServiceOrganizationResponse from(PropertyServiceOrganizationDetails details) {
        PropertyServiceOrganization organization = details.organization();
        return new PropertyServiceOrganizationResponse(
                organization.organizationId(), organization.tenantId(), organization.enterpriseId(),
                details.enterprise().legalName(), details.enterprise().unifiedSocialCreditCode(),
                organization.projectDeptId(), organization.projectDeptName(), organization.serviceContactName(),
                organization.serviceContactPhone(), organization.serviceBasis().name(), organization.serviceStartDate(),
                organization.serviceEndDate(), organization.status().name(), organization.rejectionReason(),
                organization.version(), organization.submittedAt(), organization.verifiedAt(), organization.createdAt(),
                organization.updatedAt(),
                details.materials().stream().map(PropertyServiceOrganizationMaterialResponse::from).toList(),
                details.verifications().stream().map(VerificationResponse::from).toList());
    }

    /**
     * 不可变企业核验审计记录。
     */
    public record VerificationResponse(
            Long verificationId,
            String verificationMethod,
            String providerCode,
            String sourceCode,
            String providerRequestId,
            String providerResultCode,
            String verificationResult,
            String businessStatus,
            String resultMessage,
            List<String> inconsistentFields,
            String evidenceReference,
            String remark,
            Long operatorAccountId,
            Long operatorUserId,
            String operatorRoleKey,
            boolean simulated,
            Instant verifiedAt
    ) {
        static VerificationResponse from(PropertyServiceOrganizationVerification verification) {
            return new VerificationResponse(
                    verification.verificationId(), verification.verificationMethod().name(), verification.providerCode(),
                    verification.sourceCode(), verification.providerRequestId(), verification.providerResultCode(),
                    verification.verificationResult().name(), verification.businessStatus(), verification.resultMessage(),
                    verification.inconsistentFields(), verification.evidenceReference(), verification.remark(),
                    verification.operatorAccountId(), verification.operatorUserId(), verification.operatorRoleKey(),
                    verification.simulated(), verification.verifiedAt());
        }
    }
}
