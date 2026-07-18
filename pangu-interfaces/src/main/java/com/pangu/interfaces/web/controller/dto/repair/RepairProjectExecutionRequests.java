// 关联业务：校验维修工程合同、施工、材料、结算、验收、付款、披露和归档接口输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.RepairProjectExecutionCommands.ArchiveProject;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.CreateCompletionDisclosure;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.CreatePaymentRequest;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.FinalizeAcceptance;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.RecordAcceptanceParty;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.RecordContract;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.RecordCostReview;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.RecordOwnerAcceptance;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.SealCommunityAcceptance;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.StartWork;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.SubmitExecutionRecord;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.SubmitMaterialInspection;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.SubmitSettlement;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.VerifyExecutionRecord;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.VerifyMaterialInspection;
import com.pangu.application.repair.command.RepairProjectExecutionCommands.VerifySettlement;
import com.pangu.domain.model.repair.RepairAcceptanceConclusion;
import com.pangu.domain.model.repair.RepairProject.EvidenceStage;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestoneType;
import com.pangu.domain.model.repair.RepairProjectExecution.ContractPartyType;
import com.pangu.domain.model.repair.RepairProjectExecution.SignatureMethod;
import com.pangu.domain.model.repair.RepairProjectExecution.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RepairProjectExecutionRequests {

    private RepairProjectExecutionRequests() {
    }

    public record CostReviewRequest(
            @NotNull @Min(0) Integer expectedProjectVersion,
            @NotBlank @Size(max = 32) String reviewMode,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal reviewedAmount,
            @Positive Long reportAttachmentId
    ) {
        public RecordCostReview toCommand() {
            return new RecordCostReview(
                    expectedProjectVersion, reviewMode, reviewedAmount, reportAttachmentId);
        }
    }

    public record ContractRequest(
            @NotNull @Min(0) Integer expectedProjectVersion,
            @NotNull @Positive Long supplierDeptId,
            @Size(max = 160) String supplierName,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal contractAmount,
            @NotNull @Positive Long contractAttachmentId,
            @NotNull @Size(min = 2, max = 3) List<@Valid ContractSignatureRequest> signatures
    ) {
        public RecordContract toCommand() {
            return new RecordContract(
                    expectedProjectVersion, supplierDeptId, supplierName, contractAmount,
                    contractAttachmentId, signatures.stream().map(ContractSignatureRequest::toCommand).toList());
        }
    }

    public record ContractSignatureRequest(
            @NotNull ContractPartyType partyType,
            @NotBlank @Size(max = 120) String signerName,
            @Positive Long signerUserId,
            @NotNull SignatureMethod signatureMethod,
            @NotNull @Positive Long signatureAttachmentId,
            @NotNull LocalDateTime signedAt
    ) {
        RecordContract.Signature toCommand() {
            return new RecordContract.Signature(
                    partyType, signerName, signerUserId, signatureMethod, signatureAttachmentId, signedAt);
        }
    }

    public record StartWorkRequest(@NotNull @Min(0) Integer expectedProjectVersion) {
        public StartWork toCommand() {
            return new StartWork(expectedProjectVersion);
        }
    }

    public record ExecutionRecordRequest(
            @Positive Long workPointId,
            @NotNull EvidenceStage stage,
            @NotBlank @Size(max = 1000) String description,
            @NotNull LocalDateTime occurredAt,
            @NotEmpty List<@NotNull @Positive Long> attachmentIds
    ) {
        public SubmitExecutionRecord toCommand() {
            return new SubmitExecutionRecord(workPointId, stage, description, occurredAt, attachmentIds);
        }
    }

    public record VerificationRequest(
            @NotNull VerificationStatus status,
            @Size(max = 1000) String opinion
    ) {
        public VerifyExecutionRecord toExecutionCommand() {
            return new VerifyExecutionRecord(status, opinion);
        }

        public VerifyMaterialInspection toMaterialCommand() {
            return new VerifyMaterialInspection(status, opinion);
        }
    }

    public record MaterialInspectionRequest(
            @Positive Long workPointId,
            @NotBlank @Size(max = 160) String materialName,
            @NotBlank @Size(max = 160) String brand,
            @NotBlank @Size(max = 160) String model,
            @NotBlank @Size(max = 240) String specification,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotBlank @Size(max = 32) String unit,
            @NotBlank @Size(max = 200) String manufacturer,
            @NotNull @Positive Long qualificationAttachmentId,
            @NotEmpty List<@NotNull @Positive Long> photoAttachmentIds
    ) {
        public SubmitMaterialInspection toCommand() {
            return new SubmitMaterialInspection(
                    workPointId, materialName, brand, model, specification, quantity, unit,
                    manufacturer, qualificationAttachmentId, photoAttachmentIds);
        }
    }

    public record SettlementRequest(
            @NotNull @Positive Long settlementAttachmentId,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
            @NotEmpty List<@Valid SettlementItemRequest> items
    ) {
        public SubmitSettlement toCommand() {
            return new SubmitSettlement(
                    settlementAttachmentId, taxRate, items.stream().map(SettlementItemRequest::toCommand).toList());
        }
    }

    public record SettlementItemRequest(
            @Positive Long workPointId,
            @NotNull @DecimalMin("0") BigDecimal actualQuantity,
            @NotBlank @Size(max = 32) String unit,
            @NotNull @DecimalMin("0") BigDecimal actualUnitPrice,
            @Size(max = 1000) String varianceReason
    ) {
        SubmitSettlement.Item toCommand() {
            return new SubmitSettlement.Item(
                    workPointId, actualQuantity, unit, actualUnitPrice, varianceReason);
        }
    }

    public record SettlementVerificationRequest(
            @NotNull @Min(0) Integer expectedProjectVersion,
            boolean approved,
            @Size(max = 1000) String opinion
    ) {
        public VerifySettlement toCommand() {
            return new VerifySettlement(expectedProjectVersion, approved, opinion);
        }
    }

    public record AcceptancePartyRequest(
            @NotNull RepairAcceptanceConclusion conclusion,
            @NotBlank @Size(max = 120) String participantName,
            @Size(max = 160) String participantOrganization,
            @Size(max = 1000) String opinion,
            @Positive Long evidenceAttachmentId
    ) {
        public RecordAcceptanceParty toCommand() {
            return new RecordAcceptanceParty(
                    conclusion, participantName, participantOrganization, opinion, evidenceAttachmentId);
        }
    }

    public record OwnerAcceptanceRequest(
            @NotNull @Positive Long roomId,
            @NotNull RepairAcceptanceConclusion conclusion,
            @NotBlank @Size(max = 120) String participantName,
            @Size(max = 1000) String opinion,
            @Positive Long evidenceAttachmentId
    ) {
        public RecordOwnerAcceptance toCommand() {
            return new RecordOwnerAcceptance(
                    roomId, conclusion, participantName, opinion, evidenceAttachmentId);
        }
    }

    public record AcceptanceSealRequest(
            @NotNull @Positive Long sourceAttachmentId,
            @NotNull @Positive Long sealedAttachmentId,
            @Size(max = 500) String remark
    ) {
        public SealCommunityAcceptance toCommand() {
            return new SealCommunityAcceptance(sourceAttachmentId, sealedAttachmentId, remark);
        }
    }

    public record AcceptanceFinalizationRequest(
            @NotNull @Min(0) Integer expectedProjectVersion,
            @NotNull @Positive Long resultAttachmentId,
            @Size(max = 500) String remark
    ) {
        public FinalizeAcceptance toCommand() {
            return new FinalizeAcceptance(expectedProjectVersion, resultAttachmentId, remark);
        }
    }

    public record PaymentRequest(
            @NotNull PaymentMilestoneType milestoneType,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal requestedAmount,
            @NotEmpty List<@Valid PaymentEvidenceRequest> evidence
    ) {
        public CreatePaymentRequest toCommand() {
            return new CreatePaymentRequest(
                    milestoneType, requestedAmount,
                    evidence.stream().map(PaymentEvidenceRequest::toCommand).toList());
        }
    }

    public record PaymentEvidenceRequest(
            @NotBlank @Size(max = 64) String evidenceCode,
            @NotNull @Positive Long attachmentId
    ) {
        CreatePaymentRequest.Evidence toCommand() {
            return new CreatePaymentRequest.Evidence(evidenceCode, attachmentId);
        }
    }

    public record CompletionDisclosureRequest(
            @NotNull @Min(0) Integer expectedProjectVersion,
            @NotNull LocalDate noticeStartDate,
            @NotNull LocalDate noticeEndDate,
            @NotBlank @Size(max = 500) String postingScope,
            @NotNull @Positive Long noticeAttachmentId,
            @NotNull @Positive Long propertyReportAttachmentId,
            @NotEmpty List<@NotNull @Positive Long> sitePhotoAttachmentIds,
            @NotNull LocalDate warrantyStartDate
    ) {
        public CreateCompletionDisclosure toCommand() {
            return new CreateCompletionDisclosure(
                    expectedProjectVersion, noticeStartDate, noticeEndDate, postingScope,
                    noticeAttachmentId, propertyReportAttachmentId, sitePhotoAttachmentIds,
                    warrantyStartDate);
        }
    }

    public record ArchiveRequest(@NotNull @Min(0) Integer expectedProjectVersion) {
        public ArchiveProject toCommand() {
            return new ArchiveProject(expectedProjectVersion);
        }
    }
}
