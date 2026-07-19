// 关联业务：实现维修工程楼栋治理、业主大会事项关联、接龙明细和治理依据数据访问。
package com.pangu.infrastructure.repository;

import com.pangu.domain.model.repair.RepairLocalDecisionChannel;
import com.pangu.domain.model.repair.RepairLocalDecisionScopeType;
import com.pangu.domain.model.repair.RepairProjectGovernance;
import com.pangu.domain.model.repair.RepairProjectGovernance.AssemblySubjectLink;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingDecision;
import com.pangu.domain.model.repair.RepairProjectGovernance.BuildingProcess;
import com.pangu.domain.model.repair.RepairProjectGovernance.DecisionEntry;
import com.pangu.domain.model.repair.RepairProjectGovernance.DecisionPolicySnapshot;
import com.pangu.domain.model.repair.RepairProjectGovernance.GovernanceBasis;
import com.pangu.domain.model.repair.RepairProjectGovernance.OwnerDecisionTask;
import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairVoteChoice;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.repository.RepairProjectGovernanceRepository;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.AssemblySubjectLinkRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.BuildingDecisionRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.BuildingProcessRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.DecisionEntryRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.GovernanceBasisRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.PolicySnapshotRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.ProjectSealUsageRow;
import com.pangu.infrastructure.persistence.entity.RepairProjectGovernanceRows.OwnerDecisionTaskRow;
import com.pangu.infrastructure.persistence.mapper.RepairProjectGovernanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RepairProjectGovernanceRepositoryImpl implements RepairProjectGovernanceRepository {

    private final RepairProjectGovernanceMapper mapper;

    @Override
    public DecisionPolicySnapshot insertPolicySnapshot(DecisionPolicySnapshot snapshot) {
        PolicySnapshotRow row = toRow(snapshot);
        mapper.insertPolicySnapshot(row);
        return findPolicySnapshot(row.getPolicySnapshotId(), snapshot.tenantId()).orElseThrow();
    }

    @Override
    public Optional<DecisionPolicySnapshot> findPolicySnapshot(Long policySnapshotId, Long tenantId) {
        return Optional.ofNullable(mapper.findPolicySnapshot(policySnapshotId, tenantId)).map(this::toDomain);
    }

    @Override
    public BuildingDecision insertBuildingDecision(BuildingDecision decision) {
        BuildingDecisionRow row = toRow(decision);
        mapper.insertBuildingDecision(row);
        return findBuildingDecision(row.getDecisionId(), decision.tenantId()).orElseThrow();
    }

    @Override
    public Optional<BuildingDecision> findBuildingDecision(Long decisionId, Long tenantId) {
        return Optional.ofNullable(mapper.findBuildingDecision(decisionId, tenantId)).map(this::toDomain);
    }

    @Override
    public void insertDecisionEntry(Long decisionId, Long tenantId, DecisionEntry entry, Long verifiedByUserId) {
        mapper.insertDecisionEntry(decisionId, tenantId, toRow(entry), verifiedByUserId);
    }

    @Override
    public void insertDecisionEvidence(Long decisionId, Long tenantId, String attachmentHash,
                                       Long uploadedByAccountId, Long uploadedByUserId) {
        mapper.insertDecisionEvidence(
                decisionId, tenantId, attachmentHash, uploadedByAccountId, uploadedByUserId);
    }

    @Override
    public int completeBuildingDecision(
            Long decisionId,
            Long tenantId,
            int participatedOwnerCount,
            BigDecimal participatedArea,
            int agreeOwnerCount,
            BigDecimal agreeArea,
            int disagreeOwnerCount,
            BigDecimal disagreeArea,
            int abstainOwnerCount,
            BigDecimal abstainArea,
            int invalidOwnerCount,
            BigDecimal invalidArea,
            String evidenceAttachmentHash,
            String result) {
        return mapper.completeBuildingDecision(
                decisionId, tenantId, participatedOwnerCount, participatedArea,
                agreeOwnerCount, agreeArea, disagreeOwnerCount, disagreeArea,
                abstainOwnerCount, abstainArea, invalidOwnerCount, invalidArea,
                evidenceAttachmentHash, result);
    }

    @Override
    public int completeBuildingDecisionByConfirmation(
            Long decisionId, Long tenantId, String evidenceAttachmentHash, String result) {
        return mapper.completeBuildingDecisionByConfirmation(
                decisionId, tenantId, evidenceAttachmentHash, result);
    }

    @Override
    public List<DecisionEntry> listDecisionEntries(Long decisionId, Long tenantId) {
        return mapper.listDecisionEntries(decisionId, tenantId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<DecisionEntry> listDecisionRoomParticipations(Long decisionId, Long tenantId) {
        return mapper.listDecisionRoomParticipations(decisionId, tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<OwnerDecisionTask> listOwnerDecisionTasks(Long ownerUid, Long tenantId) {
        return mapper.listOwnerDecisionTasks(null, ownerUid, tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<OwnerDecisionTask> findOwnerDecisionTask(
            Long decisionId, Long roomId, Long ownerUid, Long tenantId) {
        return Optional.ofNullable(mapper.findOwnerDecisionTask(decisionId, roomId, ownerUid, tenantId))
                .map(this::toDomain);
    }

    @Override
    public Optional<OwnerDecisionTask> findOwnerDecisionTask(
            Long decisionId, Long ownerUid, Long tenantId) {
        return findOwnerDecisionTask(decisionId, null, ownerUid, tenantId);
    }

    @Override
    public void submitOwnerDecisionVote(
            Long decisionId, Long tenantId, Long roomId, Long ownerUid,
            Long accountId, String choice, BigDecimal buildArea) {
        mapper.submitOwnerDecisionVote(
                decisionId, tenantId, roomId, ownerUid, accountId, choice, buildArea);
    }

    @Override
    public BuildingProcess insertBuildingProcess(BuildingProcess process) {
        BuildingProcessRow row = toRow(process);
        mapper.insertBuildingProcess(row);
        return findBuildingProcess(process.projectId(), process.planId(), process.tenantId()).orElseThrow();
    }

    @Override
    public Optional<BuildingProcess> findBuildingProcess(Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findBuildingProcess(projectId, planId, tenantId)).map(this::toDomain);
    }

    @Override
    public Optional<BuildingProcess> findBuildingProcessForUpdate(Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findBuildingProcessForUpdate(projectId, planId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public int updateBuildingProcessStatus(Long processId, Long tenantId, String expectedStatus,
                                           String nextStatus, Integer expectedVersion) {
        return mapper.updateBuildingProcessStatus(
                processId, tenantId, expectedStatus, nextStatus, expectedVersion);
    }

    @Override
    public int attachOfficialDocument(Long processId, Long tenantId, Long attachmentId, Integer expectedVersion) {
        return mapper.attachOfficialDocument(processId, tenantId, attachmentId, expectedVersion);
    }

    @Override
    public int recordPriceReview(Long processId, Long tenantId, String reviewMode,
                                 BigDecimal reviewedAmount, Long reportAttachmentId,
                                 String conclusion, String opinion, Long reviewedByUserId,
                                 Integer expectedVersion) {
        return mapper.recordPriceReview(
                processId, tenantId, reviewMode, reviewedAmount, reportAttachmentId,
                conclusion, opinion, reviewedByUserId, expectedVersion);
    }

    @Override
    public int recordCommitteeApproval(Long processId, Long tenantId, Long approvedByUserId,
                                       String approverPosition, String opinion, Integer expectedVersion) {
        return mapper.recordCommitteeApproval(
                processId, tenantId, approvedByUserId, approverPosition, opinion, expectedVersion);
    }

    @Override
    public Long insertProjectSealUsage(Long tenantId, Long projectId, String projectName,
                                       Long sourceAttachmentId, Long sealedAttachmentId,
                                       String sourceHash, String sealedHash,
                                       Long operatorUserId, String remark) {
        ProjectSealUsageRow row = new ProjectSealUsageRow();
        row.setTenantId(tenantId);
        row.setProjectId(projectId);
        row.setProjectName(projectName);
        row.setSourceAttachmentId(sourceAttachmentId);
        row.setSealedAttachmentId(sealedAttachmentId);
        row.setSourceHash(sourceHash);
        row.setSealedHash(sealedHash);
        row.setOperatorUserId(operatorUserId);
        row.setRemark(remark);
        mapper.insertProjectSealUsage(row);
        return row.getUsageId();
    }

    @Override
    public int authorizeBuildingProcess(Long processId, Long tenantId, Long sealUsageId, Integer expectedVersion) {
        return mapper.authorizeBuildingProcess(processId, tenantId, sealUsageId, expectedVersion);
    }

    @Override
    public AssemblySubjectLink insertAssemblySubjectLink(AssemblySubjectLink link) {
        AssemblySubjectLinkRow row = toRow(link);
        mapper.insertAssemblySubjectLink(row);
        return findAssemblySubjectLink(link.projectId(), link.planId(), link.tenantId()).orElseThrow();
    }

    @Override
    public Optional<AssemblySubjectLink> findAssemblySubjectLink(Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findAssemblySubjectLink(projectId, planId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public Optional<AssemblySubjectLink> findAssemblySubjectLinkForUpdate(
            Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findAssemblySubjectLinkForUpdate(projectId, planId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public int settleAssemblySubjectLink(Long linkId, Long tenantId, String result, Long settledByUserId) {
        return mapper.settleAssemblySubjectLink(linkId, tenantId, result, settledByUserId);
    }

    @Override
    public Optional<GovernanceBasis> findActiveGovernanceBasis(Long projectId, Long planId, Long tenantId) {
        return Optional.ofNullable(mapper.findActiveGovernanceBasis(projectId, planId, tenantId))
                .map(this::toDomain);
    }

    @Override
    public void insertGovernanceBasis(Long projectId, Long planId, Long tenantId,
                                      String basisType, String referenceType, Long referenceId,
                                      String snapshotHash,
                                      RepairSupplierSelectionMethod approvedSupplierSelectionMethod,
                                      SupplierSelectionEvaluationRule approvedSupplierEvaluationRule,
                                      Integer minimumInvitedSupplierCount,
                                      Integer minimumValidQuoteCount,
                                      String nonCompetitiveSelectionBasis,
                                      BigDecimal approvedBudgetAmount,
                                      Long createdByUserId) {
        mapper.insertGovernanceBasis(
                projectId, planId, tenantId, basisType, referenceType,
                referenceId, snapshotHash,
                approvedSupplierSelectionMethod == null ? null : approvedSupplierSelectionMethod.name(),
                approvedSupplierEvaluationRule == null ? null : approvedSupplierEvaluationRule.name(),
                minimumInvitedSupplierCount, minimumValidQuoteCount, nonCompetitiveSelectionBasis,
                approvedBudgetAmount,
                createdByUserId);
    }

    private GovernanceBasis toDomain(GovernanceBasisRow row) {
        return new GovernanceBasis(
                row.getBasisId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getBasisType(), row.getReferenceType(), row.getReferenceId(), row.getSnapshotHash(),
                row.getApprovedSupplierSelectionMethod() == null ? null
                        : RepairSupplierSelectionMethod.valueOf(row.getApprovedSupplierSelectionMethod()),
                row.getApprovedSupplierEvaluationRule() == null ? null
                        : SupplierSelectionEvaluationRule.valueOf(row.getApprovedSupplierEvaluationRule()),
                row.getMinimumInvitedSupplierCount(), row.getMinimumValidQuoteCount(),
                row.getNonCompetitiveSelectionBasis(), row.getApprovedBudgetAmount(),
                row.getStatus(), row.getCreatedByUserId(),
                row.getCreateTime());
    }

    private DecisionPolicySnapshot toDomain(PolicySnapshotRow row) {
        return new DecisionPolicySnapshot(
                row.getPolicySnapshotId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getRuleId(), row.getRuleName(), row.getRuleDocumentAttachmentId(),
                row.getRuleVersion(), row.getRuleHash(), row.getRuleEffectiveAt(),
                RepairLocalDecisionChannel.valueOf(row.getDecisionChannel()), row.getDeliveryRule(),
                RepairProjectGovernance.NonResponseRule.valueOf(row.getNonResponseRule()),
                row.getStatus(), row.getCreatedByUserId(), row.getCreateTime());
    }

    private PolicySnapshotRow toRow(DecisionPolicySnapshot snapshot) {
        PolicySnapshotRow row = new PolicySnapshotRow();
        row.setPolicySnapshotId(snapshot.policySnapshotId());
        row.setProjectId(snapshot.projectId());
        row.setPlanId(snapshot.planId());
        row.setTenantId(snapshot.tenantId());
        row.setRuleId(snapshot.ruleId());
        row.setRuleName(snapshot.ruleName());
        row.setRuleDocumentAttachmentId(snapshot.ruleDocumentAttachmentId());
        row.setRuleVersion(snapshot.ruleVersion());
        row.setRuleHash(snapshot.ruleHash());
        row.setRuleEffectiveAt(snapshot.ruleEffectiveAt());
        row.setDecisionChannel(snapshot.decisionChannel().name());
        row.setDeliveryRule(snapshot.deliveryRule());
        row.setNonResponseRule(snapshot.nonResponseRule().name());
        row.setStatus(snapshot.status());
        row.setCreatedByUserId(snapshot.createdByUserId());
        return row;
    }

    private BuildingDecision toDomain(BuildingDecisionRow row) {
        return new BuildingDecision(
                row.getDecisionId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getBuildingId(), RepairLocalDecisionScopeType.valueOf(row.getScopeType()),
                RepairLocalDecisionChannel.valueOf(row.getDecisionChannel()), row.getUnitName(),
                row.getScopeLabel(), row.getTotalOwnerCount(), row.getTotalArea(),
                row.getParticipatedOwnerCount(), row.getParticipatedArea(), row.getAgreeOwnerCount(),
                row.getAgreeArea(), row.getDisagreeOwnerCount(), row.getDisagreeArea(),
                row.getAbstainOwnerCount(), row.getAbstainArea(), row.getInvalidOwnerCount(),
                row.getInvalidArea(), row.getEvidenceAttachmentHash(),
                Integer.valueOf(1).equals(row.getPrintedAndAttached()), row.getResult(),
                row.getCreateTime(), row.getUpdateTime());
    }

    private BuildingDecisionRow toRow(BuildingDecision decision) {
        BuildingDecisionRow row = new BuildingDecisionRow();
        row.setDecisionId(decision.decisionId());
        row.setProjectId(decision.projectId());
        row.setPlanId(decision.planId());
        row.setTenantId(decision.tenantId());
        row.setBuildingId(decision.buildingId());
        row.setScopeType(decision.scopeType().name());
        row.setDecisionChannel(decision.decisionChannel().name());
        row.setUnitName(decision.unitName());
        row.setScopeLabel(decision.scopeLabel());
        row.setTotalOwnerCount(decision.totalOwnerCount());
        row.setTotalArea(decision.totalArea());
        row.setResult(decision.result());
        return row;
    }

    private BuildingProcess toDomain(BuildingProcessRow row) {
        return new BuildingProcess(
                row.getProcessId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getPolicySnapshotId(), row.getDecisionId(),
                RepairProjectGovernance.BuildingProcessStatus.valueOf(row.getStatus()),
                row.getOfficialDocumentAttachmentId(), row.getReviewMode(), row.getReviewedAmount(),
                row.getPriceReviewReportAttachmentId(), row.getPriceReviewConclusion(),
                row.getPriceReviewOpinion(), row.getPriceReviewedByUserId(), row.getPriceReviewedAt(),
                row.getApprovedByUserId(), row.getApproverPosition(), row.getApprovalOpinion(),
                row.getApprovedAt(), row.getSealUsageId(), row.getProcessVersion(),
                row.getCreateTime(), row.getUpdateTime());
    }

    private BuildingProcessRow toRow(BuildingProcess process) {
        BuildingProcessRow row = new BuildingProcessRow();
        row.setProcessId(process.processId());
        row.setProjectId(process.projectId());
        row.setPlanId(process.planId());
        row.setTenantId(process.tenantId());
        row.setPolicySnapshotId(process.policySnapshotId());
        row.setDecisionId(process.decisionId());
        row.setStatus(process.status().name());
        return row;
    }

    private DecisionEntry toDomain(DecisionEntryRow row) {
        return new DecisionEntry(
                row.getRoomId(), row.getOwnerUid(), row.getChoice() == null
                        ? null : RepairVoteChoice.valueOf(row.getChoice()),
                row.getBuildArea(), row.getOriginalText());
    }

    private OwnerDecisionTask toDomain(OwnerDecisionTaskRow row) {
        return new OwnerDecisionTask(
                row.getDecisionId(), row.getProjectId(), row.getPlanId(), row.getProjectNo(),
                row.getProjectName(), row.getScopeLabel(), row.getRoomId(), row.getRoomName(),
                row.getBuildArea(), row.getMyChoice() == null
                        ? null : RepairVoteChoice.valueOf(row.getMyChoice()));
    }

    private DecisionEntryRow toRow(DecisionEntry entry) {
        DecisionEntryRow row = new DecisionEntryRow();
        row.setRoomId(entry.roomId());
        row.setOwnerUid(entry.ownerUid());
        row.setChoice(entry.choice().name());
        row.setBuildArea(entry.buildArea());
        row.setOriginalText(entry.originalText());
        return row;
    }

    private AssemblySubjectLink toDomain(AssemblySubjectLinkRow row) {
        return new AssemblySubjectLink(
                row.getLinkId(), row.getProjectId(), row.getPlanId(), row.getTenantId(),
                row.getSessionId(), row.getPackageId(), row.getSubjectId(),
                RepairProjectGovernance.AssemblyLinkStatus.valueOf(row.getStatus()),
                row.getResult() == null
                        ? null
                        : RepairProjectGovernance.GovernanceResult.valueOf(row.getResult()),
                row.getLinkedByUserId(), row.getSettledByUserId(), row.getCreateTime(), row.getUpdateTime());
    }

    private AssemblySubjectLinkRow toRow(AssemblySubjectLink link) {
        AssemblySubjectLinkRow row = new AssemblySubjectLinkRow();
        row.setLinkId(link.linkId());
        row.setProjectId(link.projectId());
        row.setPlanId(link.planId());
        row.setTenantId(link.tenantId());
        row.setSessionId(link.sessionId());
        row.setPackageId(link.packageId());
        row.setSubjectId(link.subjectId());
        row.setStatus(link.status().name());
        row.setLinkedByUserId(link.linkedByUserId());
        return row;
    }
}
