// 关联业务：集中表达维修工程合同、施工、材料、结算、项目验收、付款和完工披露写入命令。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairAcceptanceConclusion;
import com.pangu.domain.model.repair.RepairProject.EvidenceStage;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestoneType;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractPartyType;
import com.pangu.domain.model.repair.RepairProjectExecution.SignatureMethod;
import com.pangu.domain.model.repair.RepairProjectExecution.VerificationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RepairProjectExecutionCommands {

    private RepairProjectExecutionCommands() {
    }

    public record RecordCostReview(
            Integer expectedProjectVersion,
            String reviewMode,
            BigDecimal reviewedAmount,
            Long reportAttachmentId
    ) {
    }

    public record RecordContract(
            Integer expectedProjectVersion,
            Long supplierDeptId,
            String supplierName,
            BigDecimal contractAmount,
            Long contractAttachmentId,
            List<Signature> signatures
    ) {
        public RecordContract {
            signatures = signatures == null ? List.of() : List.copyOf(signatures);
        }

        public record Signature(
                ContractPartyType partyType,
                String signerName,
                Long signerUserId,
                SignatureMethod signatureMethod,
                Long signatureAttachmentId,
                LocalDateTime signedAt
        ) {
        }
    }

    public record StartWork(Integer expectedProjectVersion) {
    }

    public record SubmitExecutionRecord(
            Long workPointId,
            EvidenceStage stage,
            String description,
            LocalDateTime occurredAt,
            List<Long> attachmentIds
    ) {
        public SubmitExecutionRecord {
            attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
        }
    }

    public record VerifyExecutionRecord(
            VerificationStatus status,
            String opinion
    ) {
    }

    public record SubmitMaterialInspection(
            Long workPointId,
            String materialName,
            String brand,
            String model,
            String specification,
            BigDecimal quantity,
            String unit,
            String manufacturer,
            Long qualificationAttachmentId,
            List<Long> photoAttachmentIds
    ) {
        public SubmitMaterialInspection {
            photoAttachmentIds = photoAttachmentIds == null ? List.of() : List.copyOf(photoAttachmentIds);
        }
    }

    public record VerifyMaterialInspection(
            VerificationStatus status,
            String opinion
    ) {
    }

    public record SubmitSettlement(
            Long settlementAttachmentId,
            BigDecimal taxRate,
            List<Item> items
    ) {
        public SubmitSettlement {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public record Item(
                Long workPointId,
                BigDecimal actualQuantity,
                String unit,
                BigDecimal actualUnitPrice,
                String varianceReason
        ) {
        }
    }

    public record VerifySettlement(
            Integer expectedProjectVersion,
            boolean approved,
            String opinion
    ) {
    }

    public record RecordAcceptanceParty(
            RepairAcceptanceConclusion conclusion,
            String participantName,
            String participantOrganization,
            String opinion,
            Long evidenceAttachmentId
    ) {
    }

    public record RecordOwnerAcceptance(
            Long roomId,
            RepairAcceptanceConclusion conclusion,
            String participantName,
            String opinion,
            Long evidenceAttachmentId
    ) {
    }

    public record SealCommunityAcceptance(
            Long sourceAttachmentId,
            Long sealedAttachmentId,
            String remark
    ) {
    }

    public record FinalizeAcceptance(
            Integer expectedProjectVersion,
            Long resultAttachmentId,
            String remark
    ) {
    }

    public record CreatePaymentRequest(
            PaymentMilestoneType milestoneType,
            BigDecimal requestedAmount,
            List<Evidence> evidence
    ) {
        public CreatePaymentRequest {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        public record Evidence(String evidenceCode, Long attachmentId) {
        }
    }

    public record CreateCompletionDisclosure(
            Integer expectedProjectVersion,
            LocalDate noticeStartDate,
            LocalDate noticeEndDate,
            String postingScope,
            Long noticeAttachmentId,
            Long propertyReportAttachmentId,
            List<Long> sitePhotoAttachmentIds,
            LocalDate warrantyStartDate
    ) {
        public CreateCompletionDisclosure {
            sitePhotoAttachmentIds = sitePhotoAttachmentIds == null
                    ? List.of()
                    : List.copyOf(sitePhotoAttachmentIds);
        }
    }

    public record ArchiveProject(Integer expectedProjectVersion) {
    }
}
