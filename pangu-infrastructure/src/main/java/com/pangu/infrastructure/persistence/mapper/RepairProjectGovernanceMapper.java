// 关联业务：读写维修工程楼栋治理、业主大会事项关联、接龙明细和治理依据快照。
package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.AssemblySubjectLinkRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.BuildingDecisionRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.BuildingProcessRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.DecisionEntryRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.PolicySnapshotRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.ProjectSealUsageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface RepairProjectGovernanceMapper {

    int insertPolicySnapshot(PolicySnapshotRow row);

    PolicySnapshotRow findPolicySnapshot(@Param("policySnapshotId") Long policySnapshotId,
                                         @Param("tenantId") Long tenantId);

    int insertBuildingDecision(BuildingDecisionRow row);

    BuildingDecisionRow findBuildingDecision(@Param("decisionId") Long decisionId,
                                               @Param("tenantId") Long tenantId);

    int insertDecisionEntry(@Param("decisionId") Long decisionId,
                            @Param("tenantId") Long tenantId,
                            @Param("entry") DecisionEntryRow entry,
                            @Param("verifiedByUserId") Long verifiedByUserId);

    int insertDecisionEvidence(@Param("decisionId") Long decisionId,
                               @Param("tenantId") Long tenantId,
                               @Param("attachmentHash") String attachmentHash,
                               @Param("uploadedByAccountId") Long uploadedByAccountId,
                               @Param("uploadedByUserId") Long uploadedByUserId);

    int completeBuildingDecision(@Param("decisionId") Long decisionId,
                                 @Param("tenantId") Long tenantId,
                                 @Param("participatedOwnerCount") int participatedOwnerCount,
                                 @Param("participatedArea") BigDecimal participatedArea,
                                 @Param("agreeOwnerCount") int agreeOwnerCount,
                                 @Param("agreeArea") BigDecimal agreeArea,
                                 @Param("disagreeOwnerCount") int disagreeOwnerCount,
                                 @Param("disagreeArea") BigDecimal disagreeArea,
                                 @Param("abstainOwnerCount") int abstainOwnerCount,
                                 @Param("abstainArea") BigDecimal abstainArea,
                                 @Param("invalidOwnerCount") int invalidOwnerCount,
                                 @Param("invalidArea") BigDecimal invalidArea,
                                 @Param("evidenceAttachmentHash") String evidenceAttachmentHash,
                                 @Param("result") String result);

    List<DecisionEntryRow> listDecisionEntries(@Param("decisionId") Long decisionId,
                                                @Param("tenantId") Long tenantId);

    int insertBuildingProcess(BuildingProcessRow row);

    BuildingProcessRow findBuildingProcess(@Param("projectId") Long projectId,
                                           @Param("planId") Long planId,
                                           @Param("tenantId") Long tenantId);

    BuildingProcessRow findBuildingProcessForUpdate(@Param("projectId") Long projectId,
                                                    @Param("planId") Long planId,
                                                    @Param("tenantId") Long tenantId);

    int updateBuildingProcessStatus(@Param("processId") Long processId,
                                    @Param("tenantId") Long tenantId,
                                    @Param("expectedStatus") String expectedStatus,
                                    @Param("nextStatus") String nextStatus,
                                    @Param("expectedVersion") Integer expectedVersion);

    int attachOfficialDocument(@Param("processId") Long processId,
                               @Param("tenantId") Long tenantId,
                               @Param("attachmentId") Long attachmentId,
                               @Param("expectedVersion") Integer expectedVersion);

    int recordPriceReview(@Param("processId") Long processId,
                          @Param("tenantId") Long tenantId,
                          @Param("reviewMode") String reviewMode,
                          @Param("reviewedAmount") BigDecimal reviewedAmount,
                          @Param("reportAttachmentId") Long reportAttachmentId,
                          @Param("conclusion") String conclusion,
                          @Param("opinion") String opinion,
                          @Param("reviewedByUserId") Long reviewedByUserId,
                          @Param("expectedVersion") Integer expectedVersion);

    int recordCommitteeApproval(@Param("processId") Long processId,
                                @Param("tenantId") Long tenantId,
                                @Param("approvedByUserId") Long approvedByUserId,
                                @Param("approverPosition") String approverPosition,
                                @Param("opinion") String opinion,
                                @Param("expectedVersion") Integer expectedVersion);

    int insertProjectSealUsage(ProjectSealUsageRow row);

    int authorizeBuildingProcess(@Param("processId") Long processId,
                                 @Param("tenantId") Long tenantId,
                                 @Param("sealUsageId") Long sealUsageId,
                                 @Param("expectedVersion") Integer expectedVersion);

    int insertAssemblySubjectLink(AssemblySubjectLinkRow row);

    AssemblySubjectLinkRow findAssemblySubjectLink(@Param("projectId") Long projectId,
                                                   @Param("planId") Long planId,
                                                   @Param("tenantId") Long tenantId);

    AssemblySubjectLinkRow findAssemblySubjectLinkForUpdate(@Param("projectId") Long projectId,
                                                            @Param("planId") Long planId,
                                                            @Param("tenantId") Long tenantId);

    int settleAssemblySubjectLink(@Param("linkId") Long linkId,
                                  @Param("tenantId") Long tenantId,
                                  @Param("result") String result,
                                  @Param("settledByUserId") Long settledByUserId);

    int insertGovernanceBasis(@Param("projectId") Long projectId,
                              @Param("planId") Long planId,
                              @Param("tenantId") Long tenantId,
                              @Param("basisType") String basisType,
                              @Param("referenceType") String referenceType,
                              @Param("referenceId") Long referenceId,
                              @Param("snapshotHash") String snapshotHash,
                              @Param("createdByUserId") Long createdByUserId);
}
