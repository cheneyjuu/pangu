package com.pangu.infrastructure.repository;

import com.pangu.domain.model.community.CommunityLedgerStats;
import com.pangu.domain.model.community.CommunitySettingsAudit;
import com.pangu.domain.model.community.DenominatorBreakdown;
import com.pangu.domain.model.community.DenominatorReviewRequest;
import com.pangu.domain.model.community.GovernancePolicy;
import com.pangu.domain.model.community.TenantCommunity;
import com.pangu.domain.repository.CommunitySettingsRepository;
import com.pangu.infrastructure.persistence.entity.CommunityLedgerStatsRow;
import com.pangu.infrastructure.persistence.entity.CommunitySettingsAuditRow;
import com.pangu.infrastructure.persistence.entity.DenominatorBreakdownRow;
import com.pangu.infrastructure.persistence.entity.DenominatorReviewRequestRow;
import com.pangu.infrastructure.persistence.entity.GovernancePolicyRow;
import com.pangu.infrastructure.persistence.entity.TenantCommunityRow;
import com.pangu.infrastructure.persistence.mapper.CommunitySettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CommunitySettingsRepositoryImpl implements CommunitySettingsRepository {

    private final CommunitySettingsMapper mapper;

    @Override
    public Optional<TenantCommunity> findCommunity(Long tenantId) {
        return Optional.ofNullable(mapper.selectCommunity(tenantId)).map(this::toCommunity);
    }

    @Override
    public Optional<Long> findFirstTenantId() {
        return Optional.ofNullable(mapper.selectFirstTenantId());
    }

    @Override
    public boolean tenantInDeptScope(Long deptId, Long tenantId) {
        return mapper.existsTenantInDeptScope(deptId, tenantId);
    }

    @Override
    public Optional<GovernancePolicy> findPolicy(Long policyId) {
        return Optional.ofNullable(mapper.selectPolicy(policyId)).map(this::toPolicy);
    }

    @Override
    public List<GovernancePolicy> listActivePolicies() {
        return mapper.selectActivePolicies().stream().map(this::toPolicy).toList();
    }

    @Override
    public CommunityLedgerStats calculateLiveLedgerStats(Long tenantId) {
        CommunityLedgerStatsRow row = mapper.selectLiveLedgerStats(tenantId);
        if (row == null) {
            return new CommunityLedgerStats(BigDecimal.ZERO, 0, 0, 0);
        }
        return new CommunityLedgerStats(
                row.getTotalArea() == null ? BigDecimal.ZERO : row.getTotalArea(),
                row.getOwnerCount() == null ? 0 : row.getOwnerCount(),
                row.getUnitCount() == null ? 0 : row.getUnitCount(),
                row.getBuildingCount() == null ? 0 : row.getBuildingCount());
    }

    @Override
    public List<DenominatorBreakdown> listDenominatorBreakdown(Long tenantId) {
        return mapper.selectDenominatorBreakdown(tenantId).stream()
                .map(this::toBreakdown).toList();
    }

    @Override
    public List<DenominatorReviewRequest> listPendingDenominatorRequests(Long tenantId) {
        return mapper.selectPendingDenominatorRequests(tenantId).stream()
                .map(this::toReviewRequest).toList();
    }

    @Override
    public Optional<DenominatorReviewRequest> findDenominatorReviewRequest(Long requestId) {
        return Optional.ofNullable(mapper.selectDenominatorReviewRequest(requestId))
                .map(this::toReviewRequest);
    }

    @Override
    public List<CommunitySettingsAudit> listAuditLogs(Long tenantId, int limit) {
        return mapper.selectAuditLogs(tenantId, limit).stream()
                .map(this::toAudit).toList();
    }

    @Override
    public int updateOrganization(TenantCommunity community) {
        return mapper.updateOrganization(toRow(community));
    }

    @Override
    public int updateAssetLedger(TenantCommunity community) {
        return mapper.updateAssetLedger(toRow(community));
    }

    @Override
    public int updateRules(TenantCommunity community) {
        return mapper.updateRules(toRow(community));
    }

    @Override
    public long updateStatistics(Long tenantId,
                                 BigDecimal totalArea,
                                 long ownerCount,
                                 long unitCount,
                                 long operatorUserId) {
        Long version = mapper.updateStatistics(tenantId, totalArea, ownerCount, unitCount, operatorUserId);
        return version == null ? 0 : version;
    }

    @Override
    public long insertStatisticsSnapshot(Long tenantId,
                                         long statisticsVersion,
                                         BigDecimal totalArea,
                                         long ownerCount,
                                         long itemCount,
                                         String sourceType,
                                         Long sourceRefId,
                                         String auditHash,
                                         Long operatorUserId) {
        Long id = mapper.insertStatisticsSnapshot(
                tenantId, statisticsVersion, totalArea, ownerCount, itemCount,
                sourceType, sourceRefId, auditHash, operatorUserId);
        return id == null ? 0 : id;
    }

    @Override
    public long insertDenominatorReviewRequest(Long tenantId,
                                               BigDecimal requestedTotalArea,
                                               long requestedOwnerCount,
                                               long requestedUnitCount,
                                               String reason,
                                               long requestedBy) {
        Long id = mapper.insertDenominatorReviewRequest(
                tenantId, requestedTotalArea, requestedOwnerCount, requestedUnitCount, reason, requestedBy);
        return id == null ? 0 : id;
    }

    @Override
    public int reviewDenominatorRequest(Long requestId, String status, long reviewedBy, String reviewComment) {
        return mapper.reviewDenominatorRequest(requestId, status, reviewedBy, reviewComment);
    }

    @Override
    public void insertAudit(Long tenantId, String operationType, String payloadJson, Long operatorUserId) {
        mapper.insertAudit(tenantId, operationType, payloadJson, operatorUserId);
    }

    private TenantCommunity toCommunity(TenantCommunityRow r) {
        return new TenantCommunity(
                r.getTenantId(), r.getTenantCode(), r.getTenantShortCode(), r.getTenantName(),
                r.getPropertyAreaName(), r.getPropertyAreaCode(), r.getDeveloperName(), r.getDeveloperAccountId(),
                r.getProvinceCode(), r.getProvinceName(), r.getCityCode(), r.getCityName(),
                r.getDistrictCode(), r.getDistrictName(), r.getStreetCode(), r.getStreetName(),
                r.getCommunityCode(), r.getCommunityName(), r.getAddress(),
                intVal(r.getPlannedHouseholdCount()), intVal(r.getDeliveredHouseholdCount()),
                intVal(r.getRegisteredPropertyUnitCount()), intVal(r.getRegisteredVotingOwnerCount()),
                dec(r.getTotalPlannedBuildingArea()), dec(r.getTotalExclusiveArea()),
                dec(r.getRegisteredVotingTotalArea()), dec(r.getExcludedParkingArea()), dec(r.getPublicArea()),
                intVal(r.getBuildingCount()), intVal(r.getUnitCount()), intVal(r.getParkingSpaceCount()),
                r.getPlotRatio(), flag(r.getOwnersAssemblyEstablished()), flag(r.getCommitteeEstablished()),
                r.getCurrentCommitteeTermName(), r.getTransitionOrgType(), r.getTransitionOrgStatus(),
                r.getRuleConfigId(), r.getSharedOwnershipStrategy(), flag(r.getFundManagedEnabled()),
                r.getFinancialControlConfigId(), intVal(r.getQuarterlyDisclosureDeadlineDay()),
                r.getStatisticsVersion() == null ? 1 : r.getStatisticsVersion(), r.getStatisticsUpdatedAt(),
                r.getGovernanceStatus(), r.getStatus(), r.getUpdateTime());
    }

    private TenantCommunityRow toRow(TenantCommunity c) {
        TenantCommunityRow r = new TenantCommunityRow();
        r.setTenantId(c.tenantId());
        r.setTenantName(c.tenantName());
        r.setPropertyAreaName(c.propertyAreaName());
        r.setPropertyAreaCode(c.propertyAreaCode());
        r.setDeveloperName(c.developerName());
        r.setDeveloperAccountId(c.developerAccountId());
        r.setProvinceCode(c.provinceCode());
        r.setProvinceName(c.provinceName());
        r.setCityCode(c.cityCode());
        r.setCityName(c.cityName());
        r.setDistrictCode(c.districtCode());
        r.setDistrictName(c.districtName());
        r.setStreetCode(c.streetCode());
        r.setStreetName(c.streetName());
        r.setCommunityCode(c.communityCode());
        r.setCommunityName(c.communityName());
        r.setAddress(c.address());
        r.setPlannedHouseholdCount(c.plannedHouseholdCount());
        r.setDeliveredHouseholdCount(c.deliveredHouseholdCount());
        r.setRegisteredPropertyUnitCount(c.registeredPropertyUnitCount());
        r.setTotalPlannedBuildingArea(c.totalPlannedBuildingArea());
        r.setTotalExclusiveArea(c.totalExclusiveArea());
        r.setRegisteredVotingTotalArea(c.registeredVotingTotalArea());
        r.setExcludedParkingArea(c.excludedParkingArea());
        r.setPublicArea(c.publicArea());
        r.setBuildingCount(c.buildingCount());
        r.setUnitCount(c.unitCount());
        r.setParkingSpaceCount(c.parkingSpaceCount());
        r.setPlotRatio(c.plotRatio());
        r.setOwnersAssemblyEstablished(c.ownersAssemblyEstablished() ? 1 : 0);
        r.setCommitteeEstablished(c.committeeEstablished() ? 1 : 0);
        r.setCurrentCommitteeTermName(c.currentCommitteeTermName());
        r.setTransitionOrgType(c.transitionOrgType());
        r.setTransitionOrgStatus(c.transitionOrgStatus());
        r.setRuleConfigId(c.ruleConfigId());
        r.setSharedOwnershipStrategy(c.sharedOwnershipStrategy());
        r.setFundManagedEnabled(c.fundManagedEnabled() ? 1 : 0);
        r.setFinancialControlConfigId(c.financialControlConfigId());
        r.setQuarterlyDisclosureDeadlineDay(c.quarterlyDisclosureDeadlineDay());
        return r;
    }

    private GovernancePolicy toPolicy(GovernancePolicyRow r) {
        return new GovernancePolicy(
                r.getPolicyId(), r.getPolicyCode(), r.getPolicyName(), r.getPolicyVersion(),
                r.getAbstentionStrategy(), r.getSharedOwnershipStrategy(), r.getOwnerRepresentativeStrategy(),
                r.getUnvotedOwnerStrategy(), r.getSummaryJson(), intVal(r.getStatus()), r.getEffectiveAt());
    }

    private DenominatorBreakdown toBreakdown(DenominatorBreakdownRow r) {
        return new DenominatorBreakdown(
                r.getAssetType(), longVal(r.getRegisteredUnitCount()), longVal(r.getVotingOwnerCount()),
                dec(r.getBuildingArea()), dec(r.getBaseRatio()), r.getOperationStatus());
    }

    private DenominatorReviewRequest toReviewRequest(DenominatorReviewRequestRow r) {
        return new DenominatorReviewRequest(
                r.getRequestId(), r.getTenantId(), dec(r.getRequestedTotalArea()),
                longVal(r.getRequestedOwnerCount()), longVal(r.getRequestedUnitCount()),
                r.getReason(), r.getStatus(), r.getRequestedBy(), r.getReviewedBy(),
                r.getReviewComment(), r.getCreateTime(), r.getReviewTime());
    }

    private CommunitySettingsAudit toAudit(CommunitySettingsAuditRow r) {
        return new CommunitySettingsAudit(
                r.getAuditId(), r.getTenantId(), r.getOperationType(),
                r.getOperatorUserId(), r.getCreateTime());
    }

    private int intVal(Integer v) {
        return v == null ? 0 : v;
    }

    private long longVal(Long v) {
        return v == null ? 0 : v;
    }

    private BigDecimal dec(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private boolean flag(Integer v) {
        return v != null && v == 1;
    }
}
