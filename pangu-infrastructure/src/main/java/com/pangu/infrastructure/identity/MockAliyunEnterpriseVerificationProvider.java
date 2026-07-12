// 关联业务：在开发和测试环境模拟阿里云企业二要素核验，结果明确标记为无法律效力。
package com.pangu.infrastructure.identity;

import com.pangu.domain.repository.EnterpriseVerificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(
        name = "platform.enterprise-verification.provider",
        havingValue = "mock-aliyun",
        matchIfMissing = true)
public class MockAliyunEnterpriseVerificationProvider implements EnterpriseVerificationProvider {

    @Override
    public String providerCode() {
        return "ALIYUN";
    }

    @Override
    public String displayName() {
        return "阿里云企业二要素核验";
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        if (!request.supplierAuthorizationConfirmed()) {
            throw new IllegalArgumentException("供应商未确认授权企业要素核验");
        }
        if (request.legalName() == null || request.legalName().isBlank()
                || request.unifiedSocialCreditCode() == null
                || request.unifiedSocialCreditCode().isBlank()) {
            throw new IllegalArgumentException("企业名称和统一社会信用代码不能为空");
        }
        return new VerificationResult(
                true,
                "MOCK-ALIYUN-" + UUID.randomUUID(),
                "SIMULATED_MATCH",
                "SIMULATED_ACTIVE",
                "模拟核验通过；未调用阿里云接口，不代表真实企业主体核验结论",
                List.of());
    }
}
