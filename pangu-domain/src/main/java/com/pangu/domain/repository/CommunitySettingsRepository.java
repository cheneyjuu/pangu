package com.pangu.domain.repository;

import com.pangu.domain.model.community.CommunityLedgerStats;
import com.pangu.domain.model.community.CommunityBuilding;
import com.pangu.domain.model.community.CommunitySettingsAudit;
import com.pangu.domain.model.community.DenominatorBreakdown;
import com.pangu.domain.model.community.DenominatorReviewRequest;
import com.pangu.domain.model.community.GovernancePolicy;
import com.pangu.domain.model.community.TenantCommunity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 社区设置仓储端口。
 */
public interface CommunitySettingsRepository {

    Optional<TenantCommunity> findCommunity(Long tenantId);

    Optional<Long> findFirstTenantId();

    boolean tenantInDeptScope(Long deptId, Long tenantId);

    Optional<GovernancePolicy> findPolicy(Long policyId);

    List<GovernancePolicy> listActivePolicies();

    CommunityLedgerStats calculateLiveLedgerStats(Long tenantId);

    List<CommunityBuilding> listBuildingDirectory(Long tenantId);

    List<DenominatorBreakdown> listDenominatorBreakdown(Long tenantId);

    List<DenominatorReviewRequest> listPendingDenominatorRequests(Long tenantId);

    Optional<DenominatorReviewRequest> findDenominatorReviewRequest(Long requestId);

    List<CommunitySettingsAudit> listAuditLogs(Long tenantId, int limit);

    int updateOrganization(TenantCommunity community);

    int updateAssetLedger(TenantCommunity community);

    int updateRules(TenantCommunity community);

    long updateStatistics(Long tenantId,
                          BigDecimal totalArea,
                          long ownerCount,
                          long unitCount,
                          long operatorUserId);

    long insertStatisticsSnapshot(Long tenantId,
                                  long statisticsVersion,
                                  BigDecimal totalArea,
                                  long ownerCount,
                                  long itemCount,
                                  String sourceType,
                                  Long sourceRefId,
                                  String auditHash,
                                  Long operatorUserId);

    long insertDenominatorReviewRequest(Long tenantId,
                                        BigDecimal requestedTotalArea,
                                        long requestedOwnerCount,
                                        long requestedUnitCount,
                                        String reason,
                                        long requestedBy);

    int reviewDenominatorRequest(Long requestId,
                                 String status,
                                 long reviewedBy,
                                 String reviewComment);

    void insertAudit(Long tenantId, String operationType, String payloadJson, Long operatorUserId);
}
