// 关联业务：保存物业服务企业主体核验的不可变审计记录。
package com.pangu.domain.model.propertyservice;

import java.time.Instant;
import java.util.List;

/**
 * 物业服务组织企业主体核验记录。
 */
public record PropertyServiceOrganizationVerification(
        Long verificationId,
        Long organizationId,
        String legalNameSnapshot,
        String unifiedSocialCreditCodeSnapshot,
        PropertyServiceOrganizationVerificationMethod verificationMethod,
        String providerCode,
        String sourceCode,
        String providerRequestId,
        String providerResultCode,
        PropertyServiceOrganizationVerificationResult verificationResult,
        String businessStatus,
        String resultMessage,
        List<String> inconsistentFields,
        String evidenceReference,
        String remark,
        Long operatorAccountId,
        Long operatorUserId,
        String operatorRoleKey,
        boolean simulated,
        Instant verifiedAt) {
}
