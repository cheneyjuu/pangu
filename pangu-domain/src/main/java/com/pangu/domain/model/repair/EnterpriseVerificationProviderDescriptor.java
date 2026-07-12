// 关联业务：向管理端描述当前启用的供应商企业核验平台及其是否为模拟实现。
package com.pangu.domain.model.repair;

public record EnterpriseVerificationProviderDescriptor(
        String providerCode,
        String displayName,
        boolean simulated
) {
}
