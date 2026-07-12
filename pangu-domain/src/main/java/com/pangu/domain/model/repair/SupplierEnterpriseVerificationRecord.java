// 关联业务：固化供应商企业主体每次核验的渠道、结论、操作账号和平台审计信息。
package com.pangu.domain.model.repair;

import java.time.LocalDateTime;
import java.util.List;

public record SupplierEnterpriseVerificationRecord(
        Long verificationId,
        Long tenantId,
        Long supplierDeptId,
        String legalNameSnapshot,
        String unifiedSocialCreditCodeSnapshot,
        SupplierEnterpriseVerificationMethod verificationMethod,
        String providerCode,
        String sourceCode,
        String providerRequestId,
        String providerResultCode,
        SupplierEnterpriseVerificationResult verificationResult,
        String businessStatus,
        String resultMessage,
        List<String> inconsistentFields,
        String evidenceReference,
        String remark,
        Long operatorAccountId,
        Long operatorUserId,
        String operatorRoleKey,
        boolean simulated,
        LocalDateTime verifiedAt
) {
    public SupplierEnterpriseVerificationRecord {
        inconsistentFields = inconsistentFields == null ? List.of() : List.copyOf(inconsistentFields);
    }
}
