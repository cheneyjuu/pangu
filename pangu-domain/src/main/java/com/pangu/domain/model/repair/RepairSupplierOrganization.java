package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

/** 可参与维修邀价的供应商组织。 */
public record RepairSupplierOrganization(
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
}
