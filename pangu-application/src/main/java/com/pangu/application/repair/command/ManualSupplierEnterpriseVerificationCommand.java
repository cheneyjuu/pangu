// 关联业务：承载物业人工核验供应商企业主体时的权威来源、结论和审计说明。
package com.pangu.application.repair.command;

public record ManualSupplierEnterpriseVerificationCommand(
        String unifiedSocialCreditCode,
        String sourceCode,
        String verificationResult,
        String evidenceReference,
        String remark
) {
}
