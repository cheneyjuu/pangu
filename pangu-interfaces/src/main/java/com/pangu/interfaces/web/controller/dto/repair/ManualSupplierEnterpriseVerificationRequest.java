// 关联业务：校验物业人工核验供应商企业主体时提交的来源、结论和审计说明。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ManualSupplierEnterpriseVerificationRequest(
        @NotBlank @Pattern(regexp = "[0-9A-Za-z]{18}") String unifiedSocialCreditCode,
        @NotBlank String sourceCode,
        @NotBlank String verificationResult,
        @Size(max = 500) String evidenceReference,
        @Size(max = 500) String remark
) {
}
