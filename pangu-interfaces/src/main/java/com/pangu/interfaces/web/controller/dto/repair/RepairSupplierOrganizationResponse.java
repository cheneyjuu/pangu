// 关联业务：返回维修供应商组织、账号状态及当前租户企业核验方式与审计摘要。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairSupplierOrganization;

import java.time.LocalDateTime;

public record RepairSupplierOrganizationResponse(
        Long supplierDeptId,
        String unifiedSocialCreditCode,
        String legalName,
        String contactName,
        String contactPhone,
        String verificationStatus,
        Long verificationId,
        String verificationMethod,
        String verificationProviderCode,
        String verificationSourceCode,
        boolean verificationSimulated,
        Long verifiedByAccountId,
        Long verifiedByUserId,
        LocalDateTime verifiedAt,
        String accountStatus,
        Integer activeAccountCount,
        String loginPhone,
        Long activationInvitationId,
        LocalDateTime activationInvitationExpiresAt
) {
    public static RepairSupplierOrganizationResponse from(RepairSupplierOrganization organization) {
        return new RepairSupplierOrganizationResponse(
                organization.supplierDeptId(),
                organization.unifiedSocialCreditCode(),
                organization.legalName(),
                organization.contactName(),
                organization.contactPhone(),
                organization.verificationStatus(),
                organization.verificationId(),
                organization.verificationMethod(),
                organization.verificationProviderCode(),
                organization.verificationSourceCode(),
                organization.verificationSimulated(),
                organization.verifiedByAccountId(),
                organization.verifiedByUserId(),
                organization.verifiedAt(),
                organization.accountStatus(),
                organization.activeAccountCount(),
                organization.loginPhone(),
                organization.activationInvitationId(),
                organization.activationInvitationExpiresAt());
    }
}
