// 关联业务：向管理端说明当前物业服务企业核验平台、是否为模拟实现和使用边界。
package com.pangu.application.propertyservice;

/**
 * 物业服务企业核验平台描述。
 */
public record PropertyServiceEnterpriseVerificationProviderDescriptor(
        String providerCode,
        String displayName,
        boolean simulated
) {
}
