// 关联业务：编排社区组织备案、建筑名册、计票基数、自治规则及其变更审计。
package com.pangu.application.community;

import com.pangu.application.support.PayloadHasher;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.community.CommunityLedgerStats;
import com.pangu.domain.model.community.CommunitySettingsOperation;
import com.pangu.domain.model.community.DenominatorReviewRequest;
import com.pangu.domain.model.community.GovernancePolicy;
import com.pangu.domain.model.community.TenantCommunity;
import com.pangu.domain.repository.CommunitySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 社区设置应用服务。
 */
@Service
@RequiredArgsConstructor
public class CommunitySettingsService {

    private static final String READ = "community:settings:read";
    private static final String OFFICIAL_WRITE = "community:settings:official:write";
    private static final String ASSET_WRITE = "community:settings:asset:write";
    private static final String POLICY_WRITE = "community:settings:policy:write";
    private static final String DENOMINATOR_RECONCILE = "community:settings:denominator:reconcile";

    private static final Set<String> GOVERNMENT_ROLES =
            Set.of("GOV_SUPER_ADMIN", "COMMUNITY_ADMIN", "PARTY_SECRETARY", "GOV_OPERATOR");
    private static final Set<String> PROPERTY_ROLES =
            Set.of("PROPERTY_MANAGER", "PROPERTY_STAFF");
    private static final Set<String> COMMITTEE_MEMBER_ROLES =
            Set.of("COMMITTEE_MEMBER", "COMMITTEE_SECRETARY");

    private final CommunitySettingsRepository repository;
    private final UserContextHolder userContextHolder;
    private final CommunitySettingsAuditService auditService;

    @Transactional(readOnly = true)
    public CommunitySettingsView getSettings(Long requestedTenantId) {
        UserContext ctx = requireContext();
        requirePermission(ctx, READ);
        Long tenantId = resolveTenantId(ctx, requestedTenantId);
        TenantCommunity community = loadCommunity(tenantId);
        GovernancePolicy policy = community.ruleConfigId() == null
                ? null
                : repository.findPolicy(community.ruleConfigId()).orElse(null);
        return buildView(ctx, community, policy);
    }

    @Transactional
    public CommunitySettingsView updateOrganization(Long requestedTenantId, CommunitySettingsCommands.Organization cmd) {
        UserContext ctx = requireContext();
        requirePermission(ctx, OFFICIAL_WRITE);
        Long tenantId = resolveTenantId(ctx, requestedTenantId);
        TenantCommunity current = loadCommunity(tenantId);
        TenantCommunity updated = copyOrganization(current, cmd);
        ensureUpdated(repository.updateOrganization(updated), tenantId);
        auditService.recordOrganizationChange(current, updated, ctx);
        return getSettings(tenantId);
    }

    @Transactional
    public CommunitySettingsView updateAssetLedger(Long requestedTenantId, CommunitySettingsCommands.AssetLedger cmd) {
        UserContext ctx = requireContext();
        requirePermission(ctx, ASSET_WRITE);
        Long tenantId = resolveTenantId(ctx, requestedTenantId);
        TenantCommunity current = loadCommunity(tenantId);
        verifyAssetLedgerChangeAllowed(ctx, current, cmd);
        TenantCommunity updated = copyAssetLedger(current, cmd);
        ensureUpdated(repository.updateAssetLedger(updated), tenantId);
        auditService.recordAssetLedgerChange(current, updated, ctx);
        return getSettings(tenantId);
    }

    @Transactional
    public CommunitySettingsView updateRules(Long requestedTenantId, CommunitySettingsCommands.Rules cmd) {
        UserContext ctx = requireContext();
        requirePermission(ctx, POLICY_WRITE);
        Long tenantId = resolveTenantId(ctx, requestedTenantId);
        TenantCommunity current = loadCommunity(tenantId);
        if (cmd.ruleConfigId() != null && repository.findPolicy(cmd.ruleConfigId()).isEmpty()) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.POLICY_NOT_FOUND,
                    "治理规则模板不存在：policyId=" + cmd.ruleConfigId());
        }
        verifyRulesChangeAllowed(ctx, current, cmd);
        TenantCommunity updated = copyRules(current, cmd);
        ensureUpdated(repository.updateRules(updated), tenantId);
        auditService.recordRulesChange(current, updated, ctx);
        return getSettings(tenantId);
    }

    @Transactional
    public CommunitySettingsView reconcileDenominator(Long requestedTenantId) {
        UserContext ctx = requireContext();
        requirePermission(ctx, DENOMINATOR_RECONCILE);
        Long tenantId = resolveTenantId(ctx, requestedTenantId);
        TenantCommunity current = loadCommunity(tenantId);
        CommunityLedgerStats stats = repository.calculateLiveLedgerStats(tenantId);
        validatePositiveStats(stats);
        long version = repository.updateStatistics(
                tenantId, stats.totalArea(), stats.ownerCount(), stats.unitCount(), ctx.userId());
        insertStatisticsSnapshot(
                tenantId, version, stats, "SYSTEM_RECALCULATE", null, ctx.userId());
        auditService.recordDenominatorRecalculation(current, stats, version, ctx);
        return getSettings(tenantId);
    }

    @Transactional
    public CommunitySettingsView submitDenominatorReview(Long requestedTenantId,
                                                         CommunitySettingsCommands.DenominatorReview cmd) {
        UserContext ctx = requireContext();
        if (!isCommitteeDirector(ctx)) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.FORBIDDEN,
                    "仅业委会主任可提交计票基数复核申请");
        }
        Long tenantId = resolveTenantId(ctx, requestedTenantId);
        TenantCommunity current = loadCommunity(tenantId);
        CommunityLedgerStats live = repository.calculateLiveLedgerStats(tenantId);
        BigDecimal requestedArea = cmd.requestedTotalArea() == null ? live.totalArea() : cmd.requestedTotalArea();
        long requestedOwnerCount = cmd.requestedOwnerCount() == null ? live.ownerCount() : cmd.requestedOwnerCount();
        long requestedUnitCount = cmd.requestedUnitCount() == null ? live.unitCount() : cmd.requestedUnitCount();
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.PARAM_INVALID,
                    "提交复核申请必须填写原因");
        }
        if (requestedArea == null || requestedArea.signum() <= 0
                || requestedOwnerCount <= 0 || requestedUnitCount <= 0) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.PARAM_INVALID,
                    "申请基数必须为正数");
        }
        long requestId = repository.insertDenominatorReviewRequest(
                tenantId, requestedArea, requestedOwnerCount, requestedUnitCount,
                cmd.reason().trim(), ctx.userId());
        auditService.recordReviewSubmission(
                current,
                requestId,
                requestedArea,
                requestedOwnerCount,
                requestedUnitCount,
                cmd.reason().trim(),
                ctx);
        return getSettings(tenantId);
    }

    @Transactional
    public CommunitySettingsView reviewDenominatorRequest(Long requestId,
                                                          CommunitySettingsCommands.ReviewDecision cmd) {
        UserContext ctx = requireContext();
        requirePermission(ctx, DENOMINATOR_RECONCILE);
        DenominatorReviewRequest request = repository.findDenominatorReviewRequest(requestId)
                .orElseThrow(() -> new CommunitySettingsApplicationException(
                        CommunitySettingsApplicationException.Reason.REVIEW_NOT_FOUND,
                        "计票基数复核申请不存在：requestId=" + requestId));
        if (!"PENDING".equals(request.status())) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.REVIEW_INVALID_STATUS,
                    "仅 PENDING 状态可复核，当前：" + request.status());
        }
        Long tenantId = resolveTenantId(ctx, request.tenantId());
        TenantCommunity current = loadCommunity(tenantId);
        String status = cmd.approved() ? "APPROVED" : "REJECTED";
        repository.reviewDenominatorRequest(requestId, status, ctx.userId(), cmd.reviewComment());
        Long statisticsVersion = null;
        if (cmd.approved()) {
            CommunityLedgerStats stats = new CommunityLedgerStats(
                    request.requestedTotalArea(),
                    request.requestedOwnerCount(),
                    request.requestedUnitCount(),
                    request.requestedUnitCount());
            long version = repository.updateStatistics(
                    tenantId, stats.totalArea(), stats.ownerCount(), stats.unitCount(), ctx.userId());
            statisticsVersion = version;
            insertStatisticsSnapshot(
                    tenantId, version, stats, "GOV_REVIEW_APPROVED", requestId, ctx.userId());
        }
        auditService.recordReviewDecision(
                current,
                request,
                cmd.approved(),
                cmd.reviewComment(),
                statisticsVersion,
                ctx);
        return getSettings(tenantId);
    }

    private CommunitySettingsView buildView(UserContext ctx, TenantCommunity community, GovernancePolicy policy) {
        CommunitySettingsView.Permissions permissions = permissions(ctx, community);
        return new CommunitySettingsView(
                community,
                policy,
                repository.listActivePolicies(),
                repository.calculateLiveLedgerStats(community.tenantId()),
                repository.listBuildingDirectory(community.tenantId()),
                repository.listDenominatorBreakdown(community.tenantId()),
                repository.listPendingDenominatorRequests(community.tenantId()),
                auditService.toView(repository.listAuditLogs(
                        community.tenantId(), visibleAuditOperationCodes(permissions), 20)),
                permissions,
                daysUntilDisclosureDeadline(community.quarterlyDisclosureDeadlineDay())
        );
    }

    private List<String> visibleAuditOperationCodes(CommunitySettingsView.Permissions permissions) {
        List<String> operations = new ArrayList<>(List.of(
                CommunitySettingsOperation.UPDATE_ASSET_LEDGER.code(),
                CommunitySettingsOperation.RECALCULATE_DENOMINATOR.code(),
                CommunitySettingsOperation.SUBMIT_DENOMINATOR_REVIEW.code(),
                CommunitySettingsOperation.REVIEW_DENOMINATOR_REQUEST.code()));
        if (permissions.canViewOrganization()) {
            operations.add(CommunitySettingsOperation.UPDATE_ORGANIZATION.code());
        }
        if (permissions.canViewRules()) {
            operations.add(CommunitySettingsOperation.UPDATE_RULES.code());
        }
        return List.copyOf(operations);
    }

    private CommunitySettingsView.Permissions permissions(UserContext ctx, TenantCommunity community) {
        boolean government = isGovernment(ctx);
        boolean committeeDirector = isCommitteeDirector(ctx);
        boolean propertyRole = PROPERTY_ROLES.contains(ctx.roleKey());
        boolean propertyStaff = "PROPERTY_STAFF".equals(ctx.roleKey());
        boolean canEditOfficialData = ctx.hasPermission(OFFICIAL_WRITE);
        boolean canEditAssetLedger = ctx.hasPermission(ASSET_WRITE);
        boolean canEditRules = ctx.hasPermission(POLICY_WRITE);
        boolean canReconcile = ctx.hasPermission(DENOMINATOR_RECONCILE);
        boolean canEditFinancialControl = government && canEditRules;
        boolean canEditLegalArea = government || (committeeDirector && community.totalExclusiveArea().signum() == 0);
        boolean canRequestReview = committeeDirector && !canReconcile;
        boolean canSubmit = canEditOfficialData || canEditAssetLedger || canEditRules || canReconcile || canRequestReview;
        return new CommunitySettingsView.Permissions(
                government,
                committeeDirector,
                propertyRole,
                !propertyStaff,
                !propertyRole,
                canEditOfficialData,
                canEditAssetLedger,
                canEditLegalArea,
                canEditRules,
                canEditFinancialControl,
                canReconcile,
                canRequestReview,
                canSubmit
        );
    }

    private Long resolveTenantId(UserContext ctx, Long requestedTenantId) {
        Long tenantId = requestedTenantId != null ? requestedTenantId : ctx.tenantId();
        if (tenantId == null) {
            tenantId = repository.findFirstTenantId()
                    .orElseThrow(() -> new CommunitySettingsApplicationException(
                            CommunitySettingsApplicationException.Reason.COMMUNITY_NOT_FOUND,
                            "尚未配置任何社区租户"));
        }
        if (ctx.tenantId() != null && !Objects.equals(ctx.tenantId(), tenantId)) {
            boolean scopedGovernment = isGovernment(ctx)
                    && ctx.deptId() != null
                    && repository.tenantInDeptScope(ctx.deptId(), tenantId);
            if (!scopedGovernment) {
                throw new CommunitySettingsApplicationException(
                        CommunitySettingsApplicationException.Reason.FORBIDDEN,
                        "目标租户不在当前工作身份数据范围内：tenantId=" + tenantId);
            }
        }
        return tenantId;
    }

    private TenantCommunity loadCommunity(Long tenantId) {
        return repository.findCommunity(tenantId)
                .orElseThrow(() -> new CommunitySettingsApplicationException(
                        CommunitySettingsApplicationException.Reason.COMMUNITY_NOT_FOUND,
                        "社区设置不存在：tenantId=" + tenantId));
    }

    private void requirePermission(UserContext ctx, String permission) {
        if (!ctx.hasPermission(permission)) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.FORBIDDEN,
                    "当前身份缺少权限：" + permission);
        }
    }

    private UserContext requireContext() {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !ctx.isSysUser()) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.FORBIDDEN,
                    "仅管理端工作身份可访问社区设置");
        }
        return ctx;
    }

    private boolean isGovernment(UserContext ctx) {
        return GOVERNMENT_ROLES.contains(ctx.roleKey());
    }

    private boolean isCommitteeDirector(UserContext ctx) {
        return "COMMITTEE_DIRECTOR".equals(ctx.roleKey());
    }

    private void verifyAssetLedgerChangeAllowed(UserContext ctx,
                                                TenantCommunity current,
                                                CommunitySettingsCommands.AssetLedger cmd) {
        if ("PROPERTY_MANAGER".equals(ctx.roleKey())) {
            if (changed(cmd.totalExclusiveArea(), current.totalExclusiveArea())
                    || changed(cmd.registeredVotingTotalArea(), current.registeredVotingTotalArea())
                    || changed(cmd.excludedParkingArea(), current.excludedParkingArea())
                    || changed(cmd.developerAccountId(), current.developerAccountId())
                    || changed(cmd.developerName(), current.developerName())
                    || changed(cmd.propertyAreaCode(), current.propertyAreaCode())) {
                throw new CommunitySettingsApplicationException(
                        CommunitySettingsApplicationException.Reason.FORBIDDEN,
                        "物业经理只能维护物理资产台账，不得修改官方编码、开发商法人或法定计票面积");
            }
        }
        if (COMMITTEE_MEMBER_ROLES.contains(ctx.roleKey())) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.FORBIDDEN,
                    "业委会成员仅可查看建筑名册，不可保存变更");
        }
        if (isCommitteeDirector(ctx)
                && current.totalExclusiveArea().signum() > 0
                && changed(cmd.totalExclusiveArea(), current.totalExclusiveArea())) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.FORBIDDEN,
                    "业委会主任只能在初次缺失时补录法定专有面积，既有基数修改需提交复核申请");
        }
    }

    private void verifyRulesChangeAllowed(UserContext ctx,
                                          TenantCommunity current,
                                          CommunitySettingsCommands.Rules cmd) {
        if (!isGovernment(ctx)) {
            if (changed(cmd.fundManagedEnabled(), current.fundManagedEnabled())
                    || changed(cmd.financialControlConfigId(), current.financialControlConfigId())
                    || changed(cmd.quarterlyDisclosureDeadlineDay(), current.quarterlyDisclosureDeadlineDay())) {
                throw new CommunitySettingsApplicationException(
                        CommunitySettingsApplicationException.Reason.FORBIDDEN,
                        "非 G 端不得修改财务公示督办与资金托管配置");
            }
        }
    }

    private TenantCommunity copyOrganization(TenantCommunity c, CommunitySettingsCommands.Organization cmd) {
        return new TenantCommunity(
                c.tenantId(), c.tenantCode(), c.tenantShortCode(), str(cmd.tenantName(), c.tenantName()),
                c.propertyAreaName(), c.propertyAreaCode(), c.developerName(), c.developerAccountId(),
                str(cmd.provinceCode(), c.provinceCode()), str(cmd.provinceName(), c.provinceName()),
                str(cmd.cityCode(), c.cityCode()), str(cmd.cityName(), c.cityName()),
                str(cmd.districtCode(), c.districtCode()), str(cmd.districtName(), c.districtName()),
                str(cmd.streetCode(), c.streetCode()), str(cmd.streetName(), c.streetName()),
                str(cmd.communityCode(), c.communityCode()), str(cmd.communityName(), c.communityName()),
                str(cmd.address(), c.address()),
                c.plannedHouseholdCount(), c.deliveredHouseholdCount(), c.registeredPropertyUnitCount(),
                c.registeredVotingOwnerCount(), c.totalPlannedBuildingArea(), c.totalExclusiveArea(),
                c.registeredVotingTotalArea(), c.excludedParkingArea(), c.publicArea(), c.buildingCount(),
                c.unitCount(), c.parkingSpaceCount(), c.plotRatio(),
                bool(cmd.ownersAssemblyEstablished(), c.ownersAssemblyEstablished()),
                bool(cmd.committeeEstablished(), c.committeeEstablished()),
                str(cmd.currentCommitteeTermName(), c.currentCommitteeTermName()),
                str(cmd.transitionOrgType(), c.transitionOrgType()),
                str(cmd.transitionOrgStatus(), c.transitionOrgStatus()),
                c.ruleConfigId(), c.sharedOwnershipStrategy(), c.repairEstimateRequired(),
                c.buildingRepairDefaultDecisionChannel(), c.propertyManagementMode(), c.fundManagedEnabled(),
                c.financialControlConfigId(), c.quarterlyDisclosureDeadlineDay(),
                c.statisticsVersion(), c.statisticsUpdatedAt(), c.governanceStatus(), c.status(), c.updateTime());
    }

    private TenantCommunity copyAssetLedger(TenantCommunity c, CommunitySettingsCommands.AssetLedger cmd) {
        return new TenantCommunity(
                c.tenantId(), c.tenantCode(), c.tenantShortCode(), c.tenantName(),
                str(cmd.propertyAreaName(), c.propertyAreaName()), str(cmd.propertyAreaCode(), c.propertyAreaCode()),
                str(cmd.developerName(), c.developerName()), val(cmd.developerAccountId(), c.developerAccountId()),
                c.provinceCode(), c.provinceName(), c.cityCode(), c.cityName(), c.districtCode(), c.districtName(),
                c.streetCode(), c.streetName(), c.communityCode(), c.communityName(), c.address(),
                nonNegative(cmd.plannedHouseholdCount(), c.plannedHouseholdCount(), "plannedHouseholdCount"),
                nonNegative(cmd.deliveredHouseholdCount(), c.deliveredHouseholdCount(), "deliveredHouseholdCount"),
                nonNegative(cmd.registeredPropertyUnitCount(), c.registeredPropertyUnitCount(), "registeredPropertyUnitCount"),
                c.registeredVotingOwnerCount(),
                nonNegative(cmd.totalPlannedBuildingArea(), c.totalPlannedBuildingArea(), "totalPlannedBuildingArea"),
                nonNegative(cmd.totalExclusiveArea(), c.totalExclusiveArea(), "totalExclusiveArea"),
                nonNegative(cmd.registeredVotingTotalArea(), c.registeredVotingTotalArea(), "registeredVotingTotalArea"),
                nonNegative(cmd.excludedParkingArea(), c.excludedParkingArea(), "excludedParkingArea"),
                nonNegative(cmd.publicArea(), c.publicArea(), "publicArea"),
                nonNegative(cmd.buildingCount(), c.buildingCount(), "buildingCount"),
                nonNegative(cmd.unitCount(), c.unitCount(), "unitCount"),
                nonNegative(cmd.parkingSpaceCount(), c.parkingSpaceCount(), "parkingSpaceCount"),
                nonNegative(cmd.plotRatio(), c.plotRatio(), "plotRatio"),
                c.ownersAssemblyEstablished(), c.committeeEstablished(), c.currentCommitteeTermName(),
                c.transitionOrgType(), c.transitionOrgStatus(), c.ruleConfigId(), c.sharedOwnershipStrategy(),
                c.repairEstimateRequired(), c.buildingRepairDefaultDecisionChannel(),
                c.propertyManagementMode(), c.fundManagedEnabled(), c.financialControlConfigId(),
                c.quarterlyDisclosureDeadlineDay(),
                c.statisticsVersion(), c.statisticsUpdatedAt(), c.governanceStatus(), c.status(), c.updateTime());
    }

    private TenantCommunity copyRules(TenantCommunity c, CommunitySettingsCommands.Rules cmd) {
        String sharedStrategy = str(cmd.sharedOwnershipStrategy(), c.sharedOwnershipStrategy());
        if (!Set.of("REPRESENTATIVE_ONLY", "PROPORTIONAL_SPLIT").contains(sharedStrategy)) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.PARAM_INVALID,
                    "sharedOwnershipStrategy 取值非法：" + sharedStrategy);
        }
        String defaultDecisionChannel = str(cmd.buildingRepairDefaultDecisionChannel(),
                c.buildingRepairDefaultDecisionChannel());
        if (!Set.of("ONLINE", "WECHAT").contains(defaultDecisionChannel)) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.PARAM_INVALID,
                    "buildingRepairDefaultDecisionChannel 取值非法：" + defaultDecisionChannel);
        }
        return new TenantCommunity(
                c.tenantId(), c.tenantCode(), c.tenantShortCode(), c.tenantName(), c.propertyAreaName(),
                c.propertyAreaCode(), c.developerName(), c.developerAccountId(), c.provinceCode(), c.provinceName(),
                c.cityCode(), c.cityName(), c.districtCode(), c.districtName(), c.streetCode(), c.streetName(),
                c.communityCode(), c.communityName(), c.address(), c.plannedHouseholdCount(),
                c.deliveredHouseholdCount(), c.registeredPropertyUnitCount(), c.registeredVotingOwnerCount(),
                c.totalPlannedBuildingArea(), c.totalExclusiveArea(), c.registeredVotingTotalArea(),
                c.excludedParkingArea(), c.publicArea(), c.buildingCount(), c.unitCount(), c.parkingSpaceCount(),
                c.plotRatio(), c.ownersAssemblyEstablished(), c.committeeEstablished(), c.currentCommitteeTermName(),
                c.transitionOrgType(), c.transitionOrgStatus(), val(cmd.ruleConfigId(), c.ruleConfigId()),
                sharedStrategy, bool(cmd.repairEstimateRequired(), c.repairEstimateRequired()),
                defaultDecisionChannel,
                c.propertyManagementMode(), bool(cmd.fundManagedEnabled(), c.fundManagedEnabled()),
                str(cmd.financialControlConfigId(), c.financialControlConfigId()),
                nonNegative(cmd.quarterlyDisclosureDeadlineDay(), c.quarterlyDisclosureDeadlineDay(),
                        "quarterlyDisclosureDeadlineDay"),
                c.statisticsVersion(), c.statisticsUpdatedAt(), c.governanceStatus(), c.status(), c.updateTime());
    }

    private void validatePositiveStats(CommunityLedgerStats stats) {
        if (stats == null || stats.totalArea() == null || stats.totalArea().signum() <= 0
                || stats.ownerCount() <= 0 || stats.unitCount() <= 0) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.PARAM_INVALID,
                    "当前业主房产台账无法形成有效计票基数");
        }
    }

    private void insertStatisticsSnapshot(Long tenantId,
                                          long version,
                                          CommunityLedgerStats stats,
                                          String sourceType,
                                          Long sourceRefId,
                                          Long operatorUserId) {
        String hashInput = tenantId + "|" + version + "|" + stats.totalArea().toPlainString()
                + "|" + stats.ownerCount() + "|" + stats.unitCount() + "|" + sourceType + "|" + sourceRefId;
        repository.insertStatisticsSnapshot(
                tenantId, version, stats.totalArea(), stats.ownerCount(), stats.unitCount(),
                sourceType, sourceRefId, PayloadHasher.sha256Hex(hashInput), operatorUserId);
    }

    private void ensureUpdated(int affected, Long tenantId) {
        if (affected == 0) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.COMMUNITY_NOT_FOUND,
                    "社区设置不存在：tenantId=" + tenantId);
        }
    }

    private int daysUntilDisclosureDeadline(int deadlineDay) {
        LocalDate today = LocalDate.now();
        int month = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate deadline = LocalDate.of(today.getYear(), month, Math.min(deadlineDay, 28));
        if (today.isAfter(deadline)) {
            LocalDate nextQuarter = deadline.plusMonths(3);
            return (int) ChronoUnit.DAYS.between(today, nextQuarter);
        }
        return (int) ChronoUnit.DAYS.between(today, deadline);
    }

    private String str(String candidate, String fallback) {
        return candidate == null ? fallback : candidate.trim();
    }

    private <T> T val(T candidate, T fallback) {
        return candidate == null ? fallback : candidate;
    }

    private boolean bool(Boolean candidate, boolean fallback) {
        return candidate == null ? fallback : candidate;
    }

    private int nonNegative(Integer candidate, int fallback, String field) {
        if (candidate == null) return fallback;
        if (candidate < 0) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.PARAM_INVALID,
                    field + " 不可为负数");
        }
        return candidate;
    }

    private BigDecimal nonNegative(BigDecimal candidate, BigDecimal fallback, String field) {
        if (candidate == null) return fallback;
        if (candidate.signum() < 0) {
            throw new CommunitySettingsApplicationException(
                    CommunitySettingsApplicationException.Reason.PARAM_INVALID,
                    field + " 不可为负数");
        }
        return candidate;
    }

    private boolean changed(BigDecimal candidate, BigDecimal current) {
        return candidate != null && current != null && candidate.compareTo(current) != 0;
    }

    private boolean changed(Object candidate, Object current) {
        return candidate != null && !Objects.equals(candidate, current);
    }
}
