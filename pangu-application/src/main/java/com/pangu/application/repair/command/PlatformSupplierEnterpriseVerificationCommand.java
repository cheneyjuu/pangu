// 关联业务：承载调用可替换企业核验平台所需的统一社会信用代码及供应商授权确认。
package com.pangu.application.repair.command;

public record PlatformSupplierEnterpriseVerificationCommand(
        String unifiedSocialCreditCode,
        boolean supplierAuthorizationConfirmed
) {
}
