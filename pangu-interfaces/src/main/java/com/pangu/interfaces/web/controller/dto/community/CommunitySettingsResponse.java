// 关联业务：向管理端输出社区法权配置、权限和可读的变更审计详情。
package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.CommunitySettingsView;
import com.pangu.domain.model.community.CommunityLedgerStats;
import com.pangu.domain.model.community.CommunityBuilding;
import com.pangu.domain.model.community.DenominatorBreakdown;
import com.pangu.domain.model.community.DenominatorReviewRequest;
import com.pangu.domain.model.community.GovernancePolicy;
import com.pangu.domain.model.community.TenantCommunity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CommunitySettingsResponse(
        Header header,
        Organization organization,
        AssetLedger assetLedger,
        Denominator denominator,
        Rules rules,
        List<AuditLog> auditLogs,
        Permissions permissions
) {

    public static CommunitySettingsResponse from(CommunitySettingsView view) {
        TenantCommunity community = view.community();
        Permissions permissions = Permissions.from(view.permissions());
        return new CommunitySettingsResponse(
                Header.from(community),
                view.permissions().canViewOrganization() ? Organization.from(community) : null,
                AssetLedger.from(community, view.liveLedgerStats(), view.buildings(),
                        view.denominatorBreakdown(), view.permissions()),
                Denominator.from(community, view.denominatorBreakdown(), view.pendingReviewRequests()),
                view.permissions().canViewRules()
                        ? Rules.from(community, view.currentPolicy(), view.policyOptions(), view.daysUntilDisclosureDeadline())
                        : null,
                view.auditLogs().stream().map(AuditLog::from).toList(),
                permissions
        );
    }

    public record Header(
            Long tenantId,
            String tenantCode,
            String tenantShortCode,
            String tenantName,
            String governanceStatus,
            long statisticsVersion,
            Instant statisticsUpdatedAt,
            Instant lastUpdatedAt
    ) {
        private static Header from(TenantCommunity c) {
            return new Header(c.tenantId(), c.tenantCode(), c.tenantShortCode(), c.tenantName(),
                    c.governanceStatus(), c.statisticsVersion(), c.statisticsUpdatedAt(), c.updateTime());
        }
    }

    public record Organization(
            String provinceCode,
            String provinceName,
            String cityCode,
            String cityName,
            String districtCode,
            String districtName,
            String streetCode,
            String streetName,
            String communityCode,
            String communityName,
            String address,
            boolean ownersAssemblyEstablished,
            boolean committeeEstablished,
            String currentCommitteeTermName,
            String transitionOrgType,
            String transitionOrgStatus
    ) {
        private static Organization from(TenantCommunity c) {
            return new Organization(c.provinceCode(), c.provinceName(), c.cityCode(), c.cityName(),
                    c.districtCode(), c.districtName(), c.streetCode(), c.streetName(),
                    c.communityCode(), c.communityName(), c.address(), c.ownersAssemblyEstablished(),
                    c.committeeEstablished(), c.currentCommitteeTermName(), c.transitionOrgType(),
                    c.transitionOrgStatus());
        }
    }

    public record AssetLedger(
            String propertyAreaName,
            String propertyAreaCode,
            String developerName,
            String developerAccountId,
            int plannedHouseholdCount,
            int deliveredHouseholdCount,
            int registeredPropertyUnitCount,
            int registeredVotingOwnerCount,
            BigDecimal totalPlannedBuildingArea,
            BigDecimal totalExclusiveArea,
            BigDecimal registeredVotingTotalArea,
            BigDecimal excludedParkingArea,
            BigDecimal publicArea,
            int buildingCount,
            int unitCount,
            int parkingSpaceCount,
            BigDecimal plotRatio,
            LedgerStats liveLedgerStats,
            List<Building> buildings,
            List<DenominatorBreakdownItem> denominatorBreakdown
    ) {
        private static AssetLedger from(TenantCommunity c,
                                        CommunityLedgerStats live,
                                        List<CommunityBuilding> buildings,
                                        List<DenominatorBreakdown> breakdown,
                                        CommunitySettingsView.Permissions permissions) {
            return new AssetLedger(c.propertyAreaName(), c.propertyAreaCode(), c.developerName(),
                    permissions.government() ? toText(c.developerAccountId()) : maskDeveloperAccount(c.developerAccountId()),
                    c.plannedHouseholdCount(), c.deliveredHouseholdCount(), c.registeredPropertyUnitCount(),
                    c.registeredVotingOwnerCount(), c.totalPlannedBuildingArea(), c.totalExclusiveArea(),
                    c.registeredVotingTotalArea(), c.excludedParkingArea(), c.publicArea(), c.buildingCount(),
                    c.unitCount(), c.parkingSpaceCount(), c.plotRatio(), LedgerStats.from(live),
                    buildings.stream().map(Building::from).toList(),
                    breakdown.stream().map(DenominatorBreakdownItem::from).toList());
        }
    }

    public record Building(
            Long buildingId,
            String buildingName,
            long unitCount,
            long roomCount
    ) {
        private static Building from(CommunityBuilding building) {
            return new Building(building.buildingId(), building.buildingName(),
                    building.unitCount(), building.roomCount());
        }
    }

    public record Denominator(
            BigDecimal legalTotalExclusiveArea,
            BigDecimal registeredVotingTotalArea,
            int registeredVotingOwnerCount,
            int registeredPropertyUnitCount,
            long statisticsVersion,
            List<DenominatorBreakdownItem> breakdown,
            List<ReviewRequest> pendingReviewRequests
    ) {
        private static Denominator from(TenantCommunity c,
                                        List<DenominatorBreakdown> breakdown,
                                        List<DenominatorReviewRequest> requests) {
            return new Denominator(c.totalExclusiveArea(), c.registeredVotingTotalArea(),
                    c.registeredVotingOwnerCount(), c.registeredPropertyUnitCount(), c.statisticsVersion(),
                    breakdown.stream().map(DenominatorBreakdownItem::from).toList(),
                    requests.stream().map(ReviewRequest::from).toList());
        }
    }

    public record Rules(
            Policy currentPolicy,
            List<Policy> policyOptions,
            String sharedOwnershipStrategy,
            boolean repairEstimateRequired,
            String buildingRepairDefaultDecisionChannel,
            boolean fundManagedEnabled,
            String financialControlConfigId,
            int quarterlyDisclosureDeadlineDay,
            int daysUntilDisclosureDeadline
    ) {
        private static Rules from(TenantCommunity c,
                                  GovernancePolicy current,
                                  List<GovernancePolicy> options,
                                  int daysUntilDisclosureDeadline) {
            return new Rules(Policy.from(current), options.stream().map(Policy::from).toList(),
                    c.sharedOwnershipStrategy(), c.repairEstimateRequired(),
                    c.buildingRepairDefaultDecisionChannel(), c.fundManagedEnabled(), c.financialControlConfigId(),
                    c.quarterlyDisclosureDeadlineDay(), daysUntilDisclosureDeadline);
        }
    }

    public record Policy(
            Long policyId,
            String policyCode,
            String policyName,
            String policyVersion,
            String abstentionStrategy,
            String sharedOwnershipStrategy,
            String ownerRepresentativeStrategy,
            String unvotedOwnerStrategy,
            String summaryJson,
            Instant effectiveAt
    ) {
        private static Policy from(GovernancePolicy p) {
            if (p == null) {
                return null;
            }
            return new Policy(p.policyId(), p.policyCode(), p.policyName(), p.policyVersion(),
                    p.abstentionStrategy(), p.sharedOwnershipStrategy(), p.ownerRepresentativeStrategy(),
                    p.unvotedOwnerStrategy(), p.summaryJson(), p.effectiveAt());
        }
    }

    public record LedgerStats(
            BigDecimal totalArea,
            long ownerCount,
            long unitCount,
            long buildingCount
    ) {
        private static LedgerStats from(CommunityLedgerStats stats) {
            return new LedgerStats(stats.totalArea(), stats.ownerCount(), stats.unitCount(), stats.buildingCount());
        }
    }

    public record DenominatorBreakdownItem(
            String assetType,
            long registeredUnitCount,
            long votingOwnerCount,
            BigDecimal buildingArea,
            BigDecimal baseRatio,
            String operationStatus
    ) {
        private static DenominatorBreakdownItem from(DenominatorBreakdown item) {
            return new DenominatorBreakdownItem(item.assetType(), item.registeredUnitCount(),
                    item.votingOwnerCount(), item.buildingArea(), item.baseRatio(), item.operationStatus());
        }
    }

    public record ReviewRequest(
            Long requestId,
            BigDecimal requestedTotalArea,
            long requestedOwnerCount,
            long requestedUnitCount,
            String reason,
            String status,
            Long requestedBy,
            Instant createTime
    ) {
        private static ReviewRequest from(DenominatorReviewRequest request) {
            return new ReviewRequest(request.requestId(), request.requestedTotalArea(),
                    request.requestedOwnerCount(), request.requestedUnitCount(), request.reason(),
                    request.status(), request.requestedBy(), request.createTime());
        }
    }

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
        private static AuditLog from(CommunitySettingsView.AuditLog audit) {
            return new AuditLog(
                    audit.auditId(),
                    audit.operationType(),
                    audit.operationLabel(),
                    audit.sectionCode(),
                    audit.summary(),
                    audit.changes().stream().map(AuditChange::from).toList(),
                    audit.reason(),
                    audit.operatorAccountId(),
                    audit.operatorUserId(),
                    audit.operatorName(),
                    audit.operatorRoleKey(),
                    audit.operatorRoleName(),
                    audit.createTime());
        }
    }

    public record AuditChange(
            String fieldCode,
            String fieldLabel,
            String beforeValue,
            String afterValue
    ) {
        private static AuditChange from(CommunitySettingsView.AuditChange change) {
            return new AuditChange(
                    change.fieldCode(), change.fieldLabel(), change.beforeValue(), change.afterValue());
        }
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
        private static Permissions from(CommunitySettingsView.Permissions p) {
            return new Permissions(p.government(), p.committeeDirector(), p.propertyRole(),
                    p.canViewOrganization(), p.canViewRules(), p.canEditOfficialData(), p.canEditAssetLedger(),
                    p.canEditLegalArea(), p.canEditRules(), p.canEditFinancialControl(),
                    p.canReconcileDenominator(), p.canRequestDenominatorReview(), p.canSubmitPageChanges());
        }
    }

    private static String toText(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String maskDeveloperAccount(Long value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value);
        if (raw.length() <= 8) {
            return "****";
        }
        return raw.substring(0, 4) + " **** **** " + raw.substring(raw.length() - 4);
    }
}
