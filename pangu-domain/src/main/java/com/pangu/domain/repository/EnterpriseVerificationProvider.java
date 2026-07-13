// 关联业务：隔离供应商、物业服务组织等企业主体核验流程与可替换外部平台。
package com.pangu.domain.repository;

import java.util.List;

public interface EnterpriseVerificationProvider {

    String providerCode();

    String displayName();

    boolean simulated();

    VerificationResult verify(VerificationRequest request);

    record VerificationRequest(
            Long tenantId,
            Long subjectId,
            String legalName,
            String unifiedSocialCreditCode,
            boolean subjectAuthorizationConfirmed
    ) {
    }

    record VerificationResult(
            boolean matched,
            String providerRequestId,
            String providerResultCode,
            String businessStatus,
            String resultMessage,
            List<String> inconsistentFields
    ) {
        public VerificationResult {
            inconsistentFields = inconsistentFields == null ? List.of() : List.copyOf(inconsistentFields);
        }
    }
}
