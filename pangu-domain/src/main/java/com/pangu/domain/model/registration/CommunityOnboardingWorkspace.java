// 关联业务：隔离租户技术开通与空间、产权、二维码、计票基数的业务就绪状态。
package com.pangu.domain.model.registration;

import java.time.Instant;

/**
 * 小区冷启动工作区。
 */
public record CommunityOnboardingWorkspace(
        Long onboardingId,
        Long applicationId,
        Long tenantId,
        String status,
        String officialAffiliationStatus,
        String spaceLedgerStatus,
        String propertyRosterStatus,
        String denominatorStatus,
        String ownerAccessQrStatus,
        Long initializationDeptId,
        Long committeeDeptId,
        Long applicantWorkUserId,
        Long createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
