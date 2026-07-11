package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairSupplierOrganizationRow {
    private Long supplierDeptId;
    private String unifiedSocialCreditCode;
    private String legalName;
    private String contactName;
    private String contactPhone;
    private String verificationStatus;
    private String accountStatus;
    private Integer activeAccountCount;
    private String loginPhone;
    private Long activationInvitationId;
    private LocalDateTime activationInvitationExpiresAt;
}
