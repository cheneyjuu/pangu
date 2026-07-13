// 关联业务：承载调用可替换企业核验平台前的企业授权确认。
package com.pangu.application.propertyservice.command;

/**
 * 物业服务企业平台核验命令。
 */
public record PlatformPropertyServiceOrganizationVerificationCommand(
        boolean enterpriseAuthorizationConfirmed
) {
}
