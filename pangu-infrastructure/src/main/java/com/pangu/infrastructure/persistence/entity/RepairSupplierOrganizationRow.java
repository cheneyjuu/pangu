package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class RepairSupplierOrganizationRow {
    private Long supplierDeptId;
    private String unifiedSocialCreditCode;
    private String legalName;
    private String contactName;
    private String contactPhone;
    private String verificationStatus;
}
