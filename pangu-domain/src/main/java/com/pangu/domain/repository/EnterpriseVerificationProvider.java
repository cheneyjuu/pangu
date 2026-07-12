// 关联业务：隔离供应商企业主体核验流程与阿里云、经授权的公示系统数据服务等外部平台。
package com.pangu.domain.repository;

import java.util.List;

public interface EnterpriseVerificationProvider {

    String providerCode();

    String displayName();

    boolean simulated();

    VerificationResult verify(VerificationRequest request);

    record VerificationRequest(
            Long tenantId,
            Long supplierDeptId,
            String legalName,
            String unifiedSocialCreditCode,
            boolean supplierAuthorizationConfirmed
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
