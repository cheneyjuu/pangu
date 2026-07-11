package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairSupplierOrganization;

public record RepairSupplierOrganizationResponse(
        Long supplierDeptId,
        String unifiedSocialCreditCode,
        String legalName,
        String contactName,
        String contactPhone,
        String verificationStatus
) {
    public static RepairSupplierOrganizationResponse from(RepairSupplierOrganization organization) {
        return new RepairSupplierOrganizationResponse(
                organization.supplierDeptId(),
                organization.unifiedSocialCreditCode(),
                organization.legalName(),
                organization.contactName(),
                organization.contactPhone(),
                organization.verificationStatus());
    }
}
