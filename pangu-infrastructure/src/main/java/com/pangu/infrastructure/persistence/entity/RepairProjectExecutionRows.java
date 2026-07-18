// 关联业务：承接维修工程合同、施工、材料、结算、项目验收、付款和完工披露的数据库行。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class RepairProjectExecutionRows {

    private RepairProjectExecutionRows() {
    }

    @Data
    public static class CostReviewRow {
        private Long reviewId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private String reviewMode;
        private BigDecimal reviewedAmount;
        private Long reportAttachmentId;
        private Long reviewedByUserId;
        private LocalDateTime reviewedAt;
    }

    @Data
    public static class ContractRow {
        private Long contractId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private Long supplierDeptId;
        private String supplierName;
        private BigDecimal contractAmount;
        private String repairScopeHash;
        private String fundSource;
        private String signingMethod;
        private Long contractAttachmentId;
        private String contractFileHash;
        private String status;
        private Long createdByUserId;
        private LocalDateTime effectiveAt;
        private LocalDateTime createTime;
    }

    @Data
    public static class ContractSignatureRow {
        private Long signatureId;
        private Long contractId;
        private String partyType;
        private String signerName;
        private Long signerUserId;
        private String signatureMethod;
        private Long signatureAttachmentId;
        private String signatureFileHash;
        private LocalDateTime signedAt;
    }

    @Data
    public static class ExecutionRecordRow {
        private Long recordId;
        private Long projectId;
        private Long planId;
        private Long itemId;
        private Long tenantId;
        private String stage;
        private String description;
        private LocalDateTime occurredAt;
        private Long submittedByUserId;
        private String verificationStatus;
        private Long verifiedByUserId;
        private String verificationOpinion;
        private LocalDateTime verifiedAt;
        private LocalDateTime createTime;
    }

    @Data
    public static class MaterialInspectionRow {
        private Long inspectionId;
        private Long projectId;
        private Long planId;
        private Long itemId;
        private Long tenantId;
        private String materialName;
        private String brand;
        private String model;
        private String specification;
        private BigDecimal quantity;
        private String unit;
        private String manufacturer;
        private Long qualificationAttachmentId;
        private Long submittedByUserId;
        private String status;
        private Long verifiedByUserId;
        private String verificationOpinion;
        private LocalDateTime verifiedAt;
        private LocalDateTime createTime;
    }

    @Data
    public static class SettlementRow {
        private Long settlementId;
        private Long projectId;
        private Long planId;
        private Long contractId;
        private Long tenantId;
        private Integer versionNo;
        private String status;
        private BigDecimal subtotalAmount;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private Long settlementAttachmentId;
        private Long submittedByUserId;
        private Long verifiedByUserId;
        private String verificationOpinion;
        private LocalDateTime submittedAt;
        private LocalDateTime verifiedAt;
    }

    @Data
    public static class SettlementItemRow {
        private Long settlementItemId;
        private Long settlementId;
        private Long projectItemId;
        private BigDecimal actualQuantity;
        private String unit;
        private BigDecimal actualUnitPrice;
        private BigDecimal amountExcludingTax;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private BigDecimal amountIncludingTax;
        private String varianceReason;
    }

    @Data
    public static class AcceptancePolicyRow {
        private Long policyId;
        private Long projectId;
        private Long planId;
        private Long tenantId;
        private String workflowType;
        private String policyHash;
        private Integer affectedOwnerCount;
        private Integer minimumAffectedOwnerParticipants;
        private String affectedOwnerPassRule;
        private BigDecimal affectedOwnerApprovalRatio;
    }

    @Data
    public static class AcceptanceRoundRow {
        private Long acceptanceId;
        private Long projectId;
        private Long policyId;
        private Long settlementId;
        private Long tenantId;
        private Integer roundNo;
        private String status;
        private Long resultProjectAttachmentId;
        private LocalDateTime submittedAt;
        private LocalDateTime completedAt;
    }

    @Data
    public static class AcceptancePartyRow {
        private Long partyId;
        private Long acceptanceId;
        private String participantKey;
        private String partyRole;
        private Long roomId;
        private Long ownerUid;
        private Long participantAccountId;
        private Long participantUserId;
        private String participantName;
        private String participantOrganization;
        private String committeePosition;
        private String conclusion;
        private String opinion;
        private String submissionMethod;
        private Long evidenceProjectAttachmentId;
        private Long sealUsageId;
        private Long submittedByUserId;
        private LocalDateTime submittedAt;
    }

    @Data
    public static class AcceptanceSummaryRow {
        private Integer participatingAffectedOwnerCount;
        private Integer passedAffectedOwnerCount;
        private Integer rectificationCount;
        private Boolean buildingLeaderPassed;
        private Boolean committeeExecutivePassed;
        private Boolean committeeSealApplied;
        private Boolean propertyTechnicalCosigned;
        private Boolean thirdPartyTechnicalCosigned;
    }

    @Data
    public static class PaymentRequestRow {
        private Long paymentRequestId;
        private Long projectId;
        private Long contractId;
        private Long tenantId;
        private String milestoneType;
        private BigDecimal requestedAmount;
        private BigDecimal cumulativeRequestedAmount;
        private BigDecimal eligibleUpperLimit;
        private String status;
        private Long requestedByUserId;
        private LocalDateTime createTime;
    }

    @Data
    public static class PaymentEvidenceRow {
        private Long paymentRequestId;
        private String evidenceCode;
        private Long attachmentId;
    }

    @Data
    public static class CompletionDisclosureRow {
        private Long disclosureId;
        private Long projectId;
        private Long tenantId;
        private LocalDate noticeStartDate;
        private LocalDate noticeEndDate;
        private String postingScope;
        private Long noticeAttachmentId;
        private Long propertyReportAttachmentId;
        private LocalDate warrantyStartDate;
        private LocalDate warrantyEndDate;
        private Long createdByUserId;
        private LocalDateTime createTime;
    }
}
