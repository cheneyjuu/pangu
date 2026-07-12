// 关联业务：聚合社区设置、计票基数、权限和可读变更记录，供管理端统一展示。
package com.pangu.application.community;

import com.pangu.domain.model.community.CommunityLedgerStats;
import com.pangu.domain.model.community.CommunityBuilding;
import com.pangu.domain.model.community.DenominatorBreakdown;
import com.pangu.domain.model.community.DenominatorReviewRequest;
import com.pangu.domain.model.community.GovernancePolicy;
import com.pangu.domain.model.community.TenantCommunity;

import java.time.Instant;
import java.util.List;

/**
 * 社区设置聚合读模型。
 */
public record CommunitySettingsView(
        TenantCommunity community,
        GovernancePolicy currentPolicy,
        List<GovernancePolicy> policyOptions,
        CommunityLedgerStats liveLedgerStats,
        List<CommunityBuilding> buildings,
        List<DenominatorBreakdown> denominatorBreakdown,
        List<DenominatorReviewRequest> pendingReviewRequests,
        List<AuditLog> auditLogs,
        Permissions permissions,
        int daysUntilDisclosureDeadline
) {
    /** 管理端可直接展示的社区设置变更记录。 */
    public record AuditLog(
            Long auditId,
            String operationType,
            String operationLabel,
            String sectionCode,
            String summary,
            List<AuditChange> changes,
            String reason,
            Long operatorAccountId,
            Long operatorUserId,
            String operatorName,
            String operatorRoleKey,
            String operatorRoleName,
            Instant createTime
    ) {
    }

    /** 单个配置字段的变更前后值。 */
    public record AuditChange(
            String fieldCode,
            String fieldLabel,
            String beforeValue,
            String afterValue
    ) {
    }

    public record Permissions(
            boolean government,
            boolean committeeDirector,
            boolean propertyRole,
            boolean canViewOrganization,
            boolean canViewRules,
            boolean canEditOfficialData,
            boolean canEditAssetLedger,
            boolean canEditLegalArea,
            boolean canEditRules,
            boolean canEditFinancialControl,
            boolean canReconcileDenominator,
            boolean canRequestDenominatorReview,
            boolean canSubmitPageChanges
    ) {
    }
}
