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

    public record DecisionPolicySnapshot(
            Long policySnapshotId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long ruleDocumentAttachmentId,
            String ruleVersion,
            String ruleHash,
            RepairLocalDecisionChannel decisionChannel,
            String deliveryRule,
            NonResponseRule nonResponseRule,
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

    public record BuildingProcessDetails(
            BuildingProcess process,
            DecisionPolicySnapshot policySnapshot,
            BuildingDecision decision,
            List<DecisionEntry> entries
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
