// 关联业务：在企业主体核验前锁定供应商名称、统一社会信用代码和租户级当前结论。
package com.pangu.domain.model.repair;

public record SupplierEnterpriseVerificationTarget(
        Long tenantId,
        Long supplierDeptId,
        String legalName,
        String unifiedSocialCreditCode,
        String verificationStatus
) {
}
