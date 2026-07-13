// 关联业务：记录属地人员手工核验物业服务企业时的来源、结论和审计凭证。
package com.pangu.interfaces.web.controller.dto.propertyservice;

import com.pangu.application.propertyservice.command.ManualPropertyServiceOrganizationVerificationCommand;
import com.pangu.domain.model.propertyservice.PropertyServiceManualVerificationSource;
import com.pangu.domain.model.propertyservice.PropertyServiceOrganizationVerificationResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 属地人工核验请求。
 */
public record ManualPropertyServiceOrganizationVerificationRequest(
        @NotNull PropertyServiceManualVerificationSource sourceCode,
        @NotNull PropertyServiceOrganizationVerificationResult verificationResult,
        @Size(max = 500) String evidenceReference,
        @Size(max = 500) String remark
) {
    public ManualPropertyServiceOrganizationVerificationCommand toCommand() {
        return new ManualPropertyServiceOrganizationVerificationCommand(
                sourceCode.name(), verificationResult.name(), evidenceReference, remark);
    }
}
