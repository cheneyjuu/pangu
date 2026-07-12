// 关联业务：映射维修供应商组织、账号状态及当前租户企业核验摘要。
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
    private Long verificationId;
    private String verificationMethod;
    private String verificationProviderCode;
    private String verificationSourceCode;
    private boolean verificationSimulated;
    private Long verifiedByAccountId;
    private Long verifiedByUserId;
    private LocalDateTime verifiedAt;
    private String accountStatus;
    private Integer activeAccountCount;
    private String loginPhone;
    private Long activationInvitationId;
    private LocalDateTime activationInvitationExpiresAt;
}
