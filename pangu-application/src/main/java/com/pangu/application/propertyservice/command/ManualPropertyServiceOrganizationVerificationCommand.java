// 关联业务：承载属地人工核验物业服务企业时的来源、结论、凭证和审计说明。
package com.pangu.application.propertyservice.command;

/**
 * 物业服务企业手工核验命令。
 */
public record ManualPropertyServiceOrganizationVerificationCommand(
        String sourceCode,
        String verificationResult,
        String evidenceReference,
        String remark
) {
}
