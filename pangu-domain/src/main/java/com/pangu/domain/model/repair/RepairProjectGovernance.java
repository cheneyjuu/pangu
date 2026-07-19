// 关联业务：表达维修工程项目的楼栋接龙治理、业主大会事项关联和不可变治理依据。
package com.pangu.domain.model.repair;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RepairProjectGovernance {

    private RepairProjectGovernance() {
    }

    public enum NonResponseRule {
        NOT_PARTICIPATED,
        FOLLOW_MAJORITY,
        ABSTAIN
    }

    public enum BuildingProcessStatus {
        DECISION_COLLECTING,
        DECISION_FAILED,
        DECISION_PASSED,
        OFFICIAL_DOCUMENT_READY,
        PRICE_REVIEWED,
        PRICE_REVIEW_REJECTED,
        COMMITTEE_APPROVED,
        AUTHORIZED
    }

    public enum AssemblyLinkStatus {
        LINKED,
        SETTLED
    }

    public enum GovernanceResult {
        PASSED,
        FAILED
    }

    /**
     * 已通过的施工单位评审规则。没有可核验的规则时，不能把报价整理结果称为最终定商。
     */
    public enum SupplierSelectionEvaluationRule {
        LOWEST_COMPLIANT_QUOTE,
        COMPREHENSIVE_EVALUATION,
        AUTHORIZED_DIRECT_SELECTION
    }

    public record DecisionPolicySnapshot(
            Long policySnapshotId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long ruleId,
            String ruleName,
            Long ruleDocumentAttachmentId,
            String ruleVersion,
            String ruleHash,
            LocalDateTime ruleEffectiveAt,
            RepairLocalDecisionChannel decisionChannel,
            String deliveryRule,
            NonResponseRule nonResponseRule,
            String status,
            Long createdByUserId,
            LocalDateTime createTime
    ) {
    }

    /**
     * 决定/授权流程产出的不可变治理依据；施工单位选择方式和规则必须与该依据一起固化。
     */
    public record GovernanceBasis(
            Long basisId,
            Long projectId,
            Long planId,
            Long tenantId,
            String basisType,
            String referenceType,
            Long referenceId,
            String snapshotHash,
            RepairSupplierSelectionMethod approvedSupplierSelectionMethod,
            SupplierSelectionEvaluationRule approvedSupplierEvaluationRule,
            Integer minimumInvitedSupplierCount,
            Integer minimumValidQuoteCount,
            String nonCompetitiveSelectionBasis,
            BigDecimal approvedBudgetAmount,
            String status,
            Long createdByUserId,
            LocalDateTime createTime
    ) {
    }

    public record BuildingDecision(
            Long decisionId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long buildingId,
            RepairLocalDecisionScopeType scopeType,
            RepairLocalDecisionChannel decisionChannel,
            String unitName,
            String scopeLabel,
            int totalOwnerCount,
            BigDecimal totalArea,
            Integer participatedOwnerCount,
            BigDecimal participatedArea,
            Integer agreeOwnerCount,
            BigDecimal agreeArea,
            Integer disagreeOwnerCount,
            BigDecimal disagreeArea,
            Integer abstainOwnerCount,
            BigDecimal abstainArea,
            Integer invalidOwnerCount,
            BigDecimal invalidArea,
            String evidenceAttachmentHash,
            boolean printedAndAttached,
            String result,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }

    public record BuildingProcess(
            Long processId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long policySnapshotId,
            Long decisionId,
            BuildingProcessStatus status,
            Long officialDocumentAttachmentId,
            String reviewMode,
            BigDecimal reviewedAmount,
            Long priceReviewReportAttachmentId,
            String priceReviewConclusion,
            String priceReviewOpinion,
            Long priceReviewedByUserId,
            LocalDateTime priceReviewedAt,
            Long approvedByUserId,
            String approverPosition,
            String approvalOpinion,
            LocalDateTime approvedAt,
            Long sealUsageId,
            Integer processVersion,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }

    public record DecisionEntry(
            Long roomId,
            Long ownerUid,
            RepairVoteChoice choice,
            BigDecimal buildArea,
            String originalText
    ) {
    }

    /** C 端业主在锁定费用承担范围内可参与的一项楼栋维修在线表决。 */
    public record OwnerDecisionTask(
            Long decisionId,
            Long projectId,
            Long planId,
            String projectNo,
            String projectName,
            String scopeLabel,
            Long roomId,
            String roomName,
            BigDecimal buildArea,
            RepairVoteChoice myChoice
    ) {
    }

    /** 管理端展示的逐房屋参与状态；个人选择仅在审计接口返回。 */
    public record DecisionRoomParticipation(
            Long roomId,
            BigDecimal buildArea,
            boolean participated,
            RepairVoteChoice choice
    ) {
    }

    public record BuildingProcessDetails(
            BuildingProcess process,
            DecisionPolicySnapshot policySnapshot,
            BuildingDecision decision,
            List<DecisionRoomParticipation> entries
    ) {
        public BuildingProcessDetails {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    public record AssemblySubjectLink(
            Long linkId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long sessionId,
            Long packageId,
            Long subjectId,
            AssemblyLinkStatus status,
            GovernanceResult result,
            Long linkedByUserId,
            Long settledByUserId,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }
}
