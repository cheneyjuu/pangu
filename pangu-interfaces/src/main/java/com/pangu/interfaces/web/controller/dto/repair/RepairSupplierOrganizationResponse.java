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
                organization.accountStatus(),
                organization.activeAccountCount(),
                organization.loginPhone(),
                organization.activationInvitationId(),
                organization.activationInvitationExpiresAt());
    }
}
