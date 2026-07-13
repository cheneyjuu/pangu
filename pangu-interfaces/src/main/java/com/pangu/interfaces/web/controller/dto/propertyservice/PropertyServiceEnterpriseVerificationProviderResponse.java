// 关联业务：告知管理端当前可用企业核验平台及其是否为开发测试模拟实现。
package com.pangu.interfaces.web.controller.dto.propertyservice;

import com.pangu.application.propertyservice.PropertyServiceEnterpriseVerificationProviderDescriptor;

/**
 * 企业核验平台描述响应。
 */
public record PropertyServiceEnterpriseVerificationProviderResponse(
        String providerCode,
        String displayName,
        boolean simulated
) {
    public static PropertyServiceEnterpriseVerificationProviderResponse from(
            PropertyServiceEnterpriseVerificationProviderDescriptor descriptor) {
        return new PropertyServiceEnterpriseVerificationProviderResponse(
                descriptor.providerCode(), descriptor.displayName(), descriptor.simulated());
    }
}
