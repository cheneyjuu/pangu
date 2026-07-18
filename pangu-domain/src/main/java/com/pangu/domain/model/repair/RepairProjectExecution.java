// 关联业务：表达维修工程项目合同、施工取证、材料进场、竣工结算、项目验收、付款和完工披露事实。
package com.pangu.domain.model.repair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RepairProjectExecution {

    private RepairProjectExecution() {
    }

    public enum ContractStatus {
        EFFECTIVE,
        VOIDED
    }

    public enum ContractPartyType {
        OWNERS_ASSEMBLY_OR_GROUP,
        PROPERTY,
        SUPPLIER
    }

    public enum SignatureMethod {
        ELECTRONIC,
        PAPER_SCAN
    }

    public enum VerificationStatus {
        PENDING,
        VERIFIED,
        REJECTED
    }

    public enum SettlementStatus {
        SUBMITTED,
        VERIFIED,
        REJECTED
    }

    public enum AcceptanceStatus {
        COLLECTING,
        RECTIFICATION_REQUIRED,
        PASSED
    }

    public enum PaymentStatus {
        PENDING_FINANCE,
        APPROVED,
        PAID,
        RETURNED,
        FAILED
    }

    public record CostReview(
            Long reviewId,
            Long projectId,
            Long planId,
            Long tenantId,
            String reviewMode,
            BigDecimal reviewedAmount,
            Long reportAttachmentId,
            Long reviewedByUserId,
            LocalDateTime reviewedAt
    ) {
    }

    public record Contract(
            Long contractId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long supplierDeptId,
            String supplierName,
            BigDecimal contractAmount,
            String repairScopeHash,
            RepairProject.FundSource fundSource,
            String signingMethod,
            Long contractAttachmentId,
            String contractFileHash,
            ContractStatus status,
            Long createdByUserId,
            LocalDateTime effectiveAt,
            LocalDateTime createTime
    ) {
    }

    public record ContractSignature(
            Long signatureId,
            Long contractId,
            ContractPartyType partyType,
            String signerName,
            Long signerUserId,
            SignatureMethod signatureMethod,
            Long signatureAttachmentId,
            String signatureFileHash,
            LocalDateTime signedAt
    ) {
    }

    public record ExecutionRecord(
            Long recordId,
            Long projectId,
            Long planId,
            Long workPointId,
            Long tenantId,
            RepairProject.EvidenceStage stage,
            String description,
            LocalDateTime occurredAt,
            Long submittedByUserId,
            Long verifiedByUserId,
            VerificationStatus verificationStatus,
            String verificationOpinion,
            LocalDateTime verifiedAt,
            List<Long> attachmentIds,
            LocalDateTime createTime
    ) {
        public ExecutionRecord {
            attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
        }
    }

    public record MaterialInspection(
            Long inspectionId,
            Long projectId,
            Long planId,
            Long workPointId,
            Long tenantId,
            String materialName,
            String brand,
            String model,
            String specification,
            BigDecimal quantity,
            String unit,
            String manufacturer,
            Long qualificationAttachmentId,
            List<Long> photoAttachmentIds,
            Long submittedByUserId,
            VerificationStatus status,
            Long verifiedByUserId,
            String verificationOpinion,
            LocalDateTime verifiedAt,
            LocalDateTime createTime
    ) {
        public MaterialInspection {
            photoAttachmentIds = photoAttachmentIds == null ? List.of() : List.copyOf(photoAttachmentIds);
        }
    }

    public record Settlement(
            Long settlementId,
            Long projectId,
            Long planId,
            Long contractId,
            Long tenantId,
            Integer versionNo,
            SettlementStatus status,
            BigDecimal subtotalAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            Long settlementAttachmentId,
            Long submittedByUserId,
            Long verifiedByUserId,
            String verificationOpinion,
            LocalDateTime submittedAt,
            LocalDateTime verifiedAt,
            List<SettlementItem> items
    ) {
        public Settlement {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record SettlementItem(
            Long settlementItemId,
            Long settlementId,
            Long workPointId,
            BigDecimal actualQuantity,
            String unit,
            BigDecimal actualUnitPrice,
            BigDecimal amountExcludingTax,
            String varianceReason
    ) {
    }

    public record AcceptancePolicy(
            Long policyId,
            Long projectId,
            Long planId,
            Long tenantId,
            RepairWorkflowType workflowType,
            String policyHash,
            int affectedOwnerCount,
            int minimumAffectedOwnerParticipants,
            RepairProject.AffectedOwnerPassRule affectedOwnerPassRule,
            BigDecimal affectedOwnerApprovalRatio
    ) {
    }

    public record AcceptanceRound(
            Long acceptanceId,
            Long projectId,
            Long policyId,
            Long settlementId,
            Long tenantId,
            int roundNo,
            AcceptanceStatus status,
            Long resultAttachmentId,
            LocalDateTime submittedAt,
            LocalDateTime completedAt
    ) {
    }

    public record AcceptanceParty(
            Long partyId,
            Long acceptanceId,
            String participantKey,
            RepairAcceptancePartyRole partyRole,
            Long roomId,
            Long ownerUid,
            Long participantAccountId,
            Long participantUserId,
            String participantName,
            String participantOrganization,
            String committeePosition,
            RepairAcceptanceConclusion conclusion,
            String opinion,
            String submissionMethod,
            Long evidenceAttachmentId,
            Long sealUsageId,
            Long submittedByUserId,
            LocalDateTime submittedAt
    ) {
    }

    public record AcceptanceSummary(
            int participatingAffectedOwnerCount,
            int passedAffectedOwnerCount,
            int rectificationCount,
            boolean buildingLeaderPassed,
            boolean committeeExecutivePassed,
            boolean committeeSealApplied,
            boolean propertyTechnicalCosigned,
            boolean thirdPartyTechnicalCosigned
    ) {
    }

    public record OwnerAcceptanceTask(
            Long projectId,
            String projectName,
            AcceptancePolicy policy,
            AcceptanceRound round,
            List<Long> affectedRoomIds,
            AcceptanceParty currentSubmission,
            AcceptanceSummary summary
    ) {
        public OwnerAcceptanceTask {
            affectedRoomIds = affectedRoomIds == null ? List.of() : List.copyOf(affectedRoomIds);
        }
    }

    public record PaymentRequest(
            Long paymentRequestId,
            Long projectId,
            Long contractId,
            Long tenantId,
            RepairProject.PaymentMilestoneType milestoneType,
            BigDecimal requestedAmount,
            BigDecimal cumulativeRequestedAmount,
            BigDecimal eligibleUpperLimit,
            PaymentStatus status,
            List<PaymentEvidence> evidence,
            Long requestedByUserId,
            LocalDateTime createTime
    ) {
        public PaymentRequest {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record PaymentEvidence(
            String evidenceCode,
            Long attachmentId
    ) {
    }

    public record CompletionDisclosure(
            Long disclosureId,
            Long projectId,
            Long tenantId,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String postingScope,
            Long noticeAttachmentId,
            Long propertyReportAttachmentId,
            List<Long> sitePhotoAttachmentIds,
            LocalDate warrantyStartDate,
            LocalDate warrantyEndDate,
            Long createdByUserId,
            LocalDateTime createTime
    ) {
        public CompletionDisclosure {
            sitePhotoAttachmentIds = sitePhotoAttachmentIds == null
                    ? List.of()
                    : List.copyOf(sitePhotoAttachmentIds);
        }
    }

    public record Details(
            Contract contract,
            List<ContractSignature> contractSignatures,
            CostReview costReview,
            List<ExecutionRecord> executionRecords,
            List<MaterialInspection> materialInspections,
            Settlement settlement,
            AcceptancePolicy acceptancePolicy,
            AcceptanceRound acceptance,
            List<AcceptanceParty> acceptanceParties,
            List<PaymentRequest> paymentRequests,
            CompletionDisclosure completionDisclosure
    ) {
        public Details {
            contractSignatures = contractSignatures == null ? List.of() : List.copyOf(contractSignatures);
            executionRecords = executionRecords == null ? List.of() : List.copyOf(executionRecords);
            materialInspections = materialInspections == null ? List.of() : List.copyOf(materialInspections);
            acceptanceParties = acceptanceParties == null ? List.of() : List.copyOf(acceptanceParties);
            paymentRequests = paymentRequests == null ? List.of() : List.copyOf(paymentRequests);
        }
    }
}
