// 关联业务：校验调用第三方供应商企业主体核验平台前的代码与授权确认。
package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PlatformSupplierEnterpriseVerificationRequest(
        @NotBlank @Pattern(regexp = "[0-9A-Za-z]{18}") String unifiedSocialCreditCode,
        @NotNull @AssertTrue Boolean supplierAuthorizationConfirmed
) {
}
