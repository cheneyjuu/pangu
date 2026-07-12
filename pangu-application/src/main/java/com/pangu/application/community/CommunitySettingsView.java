package com.pangu.application.community;

import com.pangu.domain.model.community.CommunityLedgerStats;
import com.pangu.domain.model.community.CommunityBuilding;
import com.pangu.domain.model.community.CommunitySettingsAudit;
import com.pangu.domain.model.community.DenominatorBreakdown;
import com.pangu.domain.model.community.DenominatorReviewRequest;
import com.pangu.domain.model.community.GovernancePolicy;
import com.pangu.domain.model.community.TenantCommunity;

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
        List<CommunitySettingsAudit> auditLogs,
        Permissions permissions,
        int daysUntilDisclosureDeadline
) {
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
