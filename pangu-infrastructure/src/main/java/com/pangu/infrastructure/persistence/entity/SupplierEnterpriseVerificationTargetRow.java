// 关联业务：映射供应商企业主体核验前需要加锁校验的租户级主体资料。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class SupplierEnterpriseVerificationTargetRow {
    private Long tenantId;
    private Long supplierDeptId;
    private String legalName;
    private String unifiedSocialCreditCode;
    private String verificationStatus;
}
