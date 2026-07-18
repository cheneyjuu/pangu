// 关联业务：映射维修工程楼栋治理、业主大会事项关联、接龙明细和治理依据持久化行。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class RepairProjectGovernanceRows {

    private RepairProjectGovernanceRows() {
    }

    @Data
    public static class PolicySnapshotRow {
        private Long policySnapshotId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private Long ruleId;
        private String ruleName;
        private Long ruleDocumentAttachmentId;
        private String ruleVersion;
        private String ruleHash;
        private LocalDateTime ruleEffectiveAt;
        private String decisionChannel;
        private String deliveryRule;
        private String nonResponseRule;
        private String status;
        private Long createdByUserId;
        private LocalDateTime createTime;
    }

    @Data
    public static class BuildingDecisionRow {
        private Long decisionId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private Long buildingId;
        private String scopeType;
        private String decisionChannel;
        private String unitName;
        private String scopeLabel;
        private Integer totalOwnerCount;
        private BigDecimal totalArea;
        private Integer participatedOwnerCount;
        private BigDecimal participatedArea;
        private Integer agreeOwnerCount;
        private BigDecimal agreeArea;
        private Integer disagreeOwnerCount;
        private BigDecimal disagreeArea;
        private Integer abstainOwnerCount;
        private BigDecimal abstainArea;
        private Integer invalidOwnerCount;
        private BigDecimal invalidArea;
        private String evidenceAttachmentHash;
        private Integer printedAndAttached;
        private String result;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class BuildingProcessRow {
        private Long processId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private Long policySnapshotId;
        private Long decisionId;
        private String status;
        private Long officialDocumentAttachmentId;
        private String reviewMode;
        private BigDecimal reviewedAmount;
        private Long priceReviewReportAttachmentId;
        private String priceReviewConclusion;
        private String priceReviewOpinion;
        private Long priceReviewedByUserId;
        private LocalDateTime priceReviewedAt;
        private Long approvedByUserId;
        private String approverPosition;
        private String approvalOpinion;
        private LocalDateTime approvedAt;
        private Long sealUsageId;
        private Integer processVersion;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class DecisionEntryRow {
        private Long roomId;
        private Long ownerUid;
        private String choice;
        private BigDecimal buildArea;
        private String originalText;
    }

    @Data
    public static class OwnerDecisionTaskRow {
        private Long decisionId;
        private Long projectId;
        private Long planId;
        private String projectNo;
        private String projectName;
        private String scopeLabel;
        private Long roomId;
        private String roomName;
        private BigDecimal buildArea;
        private String myChoice;
    }

    @Data
    public static class AssemblySubjectLinkRow {
        private Long linkId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private Long sessionId;
        private Long packageId;
        private Long subjectId;
        private String status;
        private String result;
        private Long linkedByUserId;
        private Long settledByUserId;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class ProjectSealUsageRow {
        private Long usageId;
        private Long tenantId;
        private Long projectId;
        private String projectName;
        private Long sourceAttachmentId;
        private Long sealedAttachmentId;
        private String sourceHash;
        private String sealedHash;
        private Long operatorUserId;
        private String remark;
    }
}
