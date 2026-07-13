// 关联业务：确认物业服务企业授权后再调用可替换的企业核验平台。
package com.pangu.interfaces.web.controller.dto.propertyservice;

import com.pangu.application.propertyservice.command.PlatformPropertyServiceOrganizationVerificationCommand;
import jakarta.validation.constraints.AssertTrue;

/**
 * 平台企业核验请求。
 */
public record PlatformPropertyServiceOrganizationVerificationRequest(
        @AssertTrue(message = "调用企业核验平台前必须确认企业授权")
        boolean enterpriseAuthorizationConfirmed
) {
    public PlatformPropertyServiceOrganizationVerificationCommand toCommand() {
        return new PlatformPropertyServiceOrganizationVerificationCommand(enterpriseAuthorizationConfirmed);
    }
}
