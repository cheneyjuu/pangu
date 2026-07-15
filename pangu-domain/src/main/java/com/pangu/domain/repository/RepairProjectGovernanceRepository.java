// 关联业务：持久化楼栋维修治理流程、业主大会事项关联、接龙明细和治理依据快照。
package com.pangu.domain.repository;

import com.pangu.domain.model.repair.RepairProjectGovernance.AssemblySubjectLink;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingDecision;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcess;
import com.pangu.domain.model.repair.RepairProjectGovernance.DecisionEntry;
import com.pangu.domain.model.repair.RepairProjectGovernance.DecisionPolicySnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RepairProjectGovernanceRepository {

    DecisionPolicySnapshot insertPolicySnapshot(DecisionPolicySnapshot snapshot);

    Optional<DecisionPolicySnapshot> findPolicySnapshot(Long policySnapshotId, Long tenantId);

    BuildingDecision insertBuildingDecision(BuildingDecision decision);

    Optional<BuildingDecision> findBuildingDecision(Long decisionId, Long tenantId);

    void insertDecisionEntry(Long decisionId, Long tenantId, DecisionEntry entry, Long verifiedByUserId);

    void insertDecisionEvidence(Long decisionId, Long tenantId, String attachmentHash,
                                Long uploadedByAccountId, Long uploadedByUserId);

    int completeBuildingDecision(Long decisionId, Long tenantId,
                                 int participatedOwnerCount, BigDecimal participatedArea,
                                 int agreeOwnerCount, BigDecimal agreeArea,
                                 int disagreeOwnerCount, BigDecimal disagreeArea,
                                 int abstainOwnerCount, BigDecimal abstainArea,
                                 int invalidOwnerCount, BigDecimal invalidArea,
                                 String evidenceAttachmentHash, String result);

    List<DecisionEntry> listDecisionEntries(Long decisionId, Long tenantId);

    BuildingProcess insertBuildingProcess(BuildingProcess process);

    Optional<BuildingProcess> findBuildingProcess(Long projectId, Long planId, Long tenantId);

    Optional<BuildingProcess> findBuildingProcessForUpdate(Long projectId, Long planId, Long tenantId);

    int updateBuildingProcessStatus(Long processId, Long tenantId, String expectedStatus,
                                    String nextStatus, Integer expectedVersion);

    int attachOfficialDocument(Long processId, Long tenantId, Long attachmentId, Integer expectedVersion);

    int recordPriceReview(Long processId, Long tenantId, String reviewMode,
                          BigDecimal reviewedAmount, Long reportAttachmentId,
                          String conclusion, String opinion, Long reviewedByUserId,
                          Integer expectedVersion);

    int recordCommitteeApproval(Long processId, Long tenantId, Long approvedByUserId,
                                String approverPosition, String opinion, Integer expectedVersion);

    Long insertProjectSealUsage(Long tenantId, Long projectId, String projectName,
                                Long sourceAttachmentId, Long sealedAttachmentId,
                                String sourceHash, String sealedHash, Long operatorUserId, String remark);

    int authorizeBuildingProcess(Long processId, Long tenantId, Long sealUsageId, Integer expectedVersion);

    AssemblySubjectLink insertAssemblySubjectLink(AssemblySubjectLink link);

    Optional<AssemblySubjectLink> findAssemblySubjectLink(Long projectId, Long planId, Long tenantId);

    Optional<AssemblySubjectLink> findAssemblySubjectLinkForUpdate(Long projectId, Long planId, Long tenantId);

    int settleAssemblySubjectLink(Long linkId, Long tenantId, String result, Long settledByUserId);

    void insertGovernanceBasis(Long projectId, Long planId, Long tenantId,
                               String basisType, String referenceType, Long referenceId,
                               String snapshotHash, Long createdByUserId);
}
