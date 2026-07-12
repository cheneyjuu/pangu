// 关联业务：向管理端展示供应商企业主体核验方式、结论及完整操作审计信息。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.SupplierEnterpriseVerificationRecord;

import java.time.LocalDateTime;
import java.util.List;

public record SupplierEnterpriseVerificationResponse(
        Long verificationId,
        Long supplierDeptId,
        String legalNameSnapshot,
        String unifiedSocialCreditCodeSnapshot,
        String verificationMethod,
        String providerCode,
        String sourceCode,
        String providerRequestId,
        String providerResultCode,
        String verificationResult,
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
    public static SupplierEnterpriseVerificationResponse from(SupplierEnterpriseVerificationRecord record) {
        return new SupplierEnterpriseVerificationResponse(
                record.verificationId(), record.supplierDeptId(), record.legalNameSnapshot(),
                record.unifiedSocialCreditCodeSnapshot(), record.verificationMethod().name(),
                record.providerCode(), record.sourceCode(), record.providerRequestId(),
                record.providerResultCode(), record.verificationResult().name(), record.businessStatus(),
                record.resultMessage(), record.inconsistentFields(), record.evidenceReference(), record.remark(),
                record.operatorAccountId(), record.operatorUserId(), record.operatorRoleKey(),
                record.simulated(), record.verifiedAt());
    }
}
