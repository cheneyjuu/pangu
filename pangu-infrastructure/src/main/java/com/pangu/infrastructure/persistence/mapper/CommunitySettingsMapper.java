package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.CommunityLedgerStatsRow;
import com.pangu.infrastructure.persistence.entity.CommunitySettingsAuditRow;
import com.pangu.infrastructure.persistence.entity.DenominatorBreakdownRow;
import com.pangu.infrastructure.persistence.entity.DenominatorReviewRequestRow;
import com.pangu.infrastructure.persistence.entity.GovernancePolicyRow;
import com.pangu.infrastructure.persistence.entity.TenantCommunityRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CommunitySettingsMapper {

    TenantCommunityRow selectCommunity(@Param("tenantId") Long tenantId);

    Long selectFirstTenantId();

    boolean existsTenantInDeptScope(@Param("deptId") Long deptId, @Param("tenantId") Long tenantId);

    GovernancePolicyRow selectPolicy(@Param("policyId") Long policyId);

    List<GovernancePolicyRow> selectActivePolicies();

    CommunityLedgerStatsRow selectLiveLedgerStats(@Param("tenantId") Long tenantId);

    List<DenominatorBreakdownRow> selectDenominatorBreakdown(@Param("tenantId") Long tenantId);

    List<DenominatorReviewRequestRow> selectPendingDenominatorRequests(@Param("tenantId") Long tenantId);

    DenominatorReviewRequestRow selectDenominatorReviewRequest(@Param("requestId") Long requestId);

    List<CommunitySettingsAuditRow> selectAuditLogs(@Param("tenantId") Long tenantId, @Param("limit") int limit);

    int updateOrganization(TenantCommunityRow row);

    int updateAssetLedger(TenantCommunityRow row);

    int updateRules(TenantCommunityRow row);

    Long updateStatistics(@Param("tenantId") Long tenantId,
                          @Param("totalArea") BigDecimal totalArea,
                          @Param("ownerCount") long ownerCount,
                          @Param("unitCount") long unitCount,
                          @Param("operatorUserId") long operatorUserId);

    Long insertStatisticsSnapshot(@Param("tenantId") Long tenantId,
                                  @Param("statisticsVersion") long statisticsVersion,
                                  @Param("totalArea") BigDecimal totalArea,
                                  @Param("ownerCount") long ownerCount,
                                  @Param("itemCount") long itemCount,
                                  @Param("sourceType") String sourceType,
                                  @Param("sourceRefId") Long sourceRefId,
                                  @Param("auditHash") String auditHash,
                                  @Param("operatorUserId") Long operatorUserId);

    Long insertDenominatorReviewRequest(@Param("tenantId") Long tenantId,
                                        @Param("requestedTotalArea") BigDecimal requestedTotalArea,
                                        @Param("requestedOwnerCount") long requestedOwnerCount,
                                        @Param("requestedUnitCount") long requestedUnitCount,
                                        @Param("reason") String reason,
                                        @Param("requestedBy") long requestedBy);

    int reviewDenominatorRequest(@Param("requestId") Long requestId,
                                 @Param("status") String status,
                                 @Param("reviewedBy") long reviewedBy,
                                 @Param("reviewComment") String reviewComment);

    int insertAudit(@Param("tenantId") Long tenantId,
                    @Param("operationType") String operationType,
                    @Param("payloadJson") String payloadJson,
                    @Param("operatorUserId") Long operatorUserId);
}
