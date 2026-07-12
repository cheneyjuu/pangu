// 关联业务：向管理端暴露当前企业核验平台名称、代码和模拟状态，避免前端绑定具体服务商。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.EnterpriseVerificationProviderDescriptor;

public record EnterpriseVerificationProviderResponse(
        String providerCode,
        String displayName,
        boolean simulated
) {
    public static EnterpriseVerificationProviderResponse from(EnterpriseVerificationProviderDescriptor descriptor) {
        return new EnterpriseVerificationProviderResponse(
                descriptor.providerCode(), descriptor.displayName(), descriptor.simulated());
    }
}
