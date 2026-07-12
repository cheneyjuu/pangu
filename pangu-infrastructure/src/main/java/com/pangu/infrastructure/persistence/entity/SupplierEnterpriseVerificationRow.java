// 关联业务：映射供应商企业主体核验渠道、结果和操作人审计记录。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupplierEnterpriseVerificationRow {
    private Long verificationId;
    private Long tenantId;
    private Long supplierDeptId;
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
    private boolean simulated;
    private LocalDateTime verifiedAt;
}
