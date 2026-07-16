// 关联业务：校验维修工程结构化实施方案、工程项、施工证据、验收规则和付款节点输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.RepairPlanDraftCommand;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule;
import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.EvidenceRequirement;
import com.pangu.domain.model.repair.RepairProject.EvidenceStage;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestone;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestoneType;
import com.pangu.domain.model.repair.RepairProject.SettlementMethod;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RepairPlanRequest(
        @NotBlank @Size(max = 4000) String problemCause,
        @NotBlank @Size(max = 8000) String implementationScope,
        @NotNull @DecimalMin(value = "0.01") BigDecimal budgetTotal,
        @NotNull RepairSupplierSelectionMethod supplierSelectionMethod,
        @NotBlank @Size(max = 1000) String supplierSelectionReason,
        @NotBlank @Size(max = 8000) String constructionManagementRequirements,
        @NotEmpty List<@Valid EvidenceRequirementRequest> evidenceRequirements,
        @NotBlank @Size(max = 8000) String safetyRequirements,
        @NotBlank @Size(max = 4000) String acceptanceMethod,
        @Size(max = 1000) String affectedOwnerScopeDescription,
        @Min(1) Integer minimumAffectedOwnerAcceptors,
        AffectedOwnerPassRule affectedOwnerPassRule,
        @DecimalMin(value = "0.0001") @DecimalMax("1.0000") BigDecimal affectedOwnerApprovalRatio,
        @NotNull SettlementMethod settlementMethod,
        @NotNull LocalDate plannedStartDate,
        @NotNull LocalDate plannedCompletionDate,
        @NotNull @Min(0) Integer warrantyDays,
        boolean priceReviewRequired,
        @NotEmpty List<@Valid PaymentMilestoneRequest> paymentMilestones,
        @NotEmpty List<@Valid ItemRequest> items,
        List<@Valid AttachmentReferenceRequest> attachments
) {

    public RepairPlanDraftCommand toCommand() {
        return new RepairPlanDraftCommand(
                problemCause, implementationScope, budgetTotal,
                supplierSelectionMethod, supplierSelectionReason,
                constructionManagementRequirements,
                evidenceRequirements.stream().map(EvidenceRequirementRequest::toDomain).toList(),
                safetyRequirements, acceptanceMethod, affectedOwnerScopeDescription,
                minimumAffectedOwnerAcceptors, affectedOwnerPassRule, affectedOwnerApprovalRatio,
                settlementMethod, plannedStartDate, plannedCompletionDate, warrantyDays,
                priceReviewRequired,
                paymentMilestones.stream().map(PaymentMilestoneRequest::toDomain).toList(),
                items.stream().map(ItemRequest::toCommand).toList(),
                attachments == null
                        ? List.of()
                        : attachments.stream().map(AttachmentReferenceRequest::toCommand).toList());
    }

    public record EvidenceRequirementRequest(
            @NotNull EvidenceStage stage,
            @NotBlank @Size(max = 1000) String description,
            boolean required
    ) {
        EvidenceRequirement toDomain() {
            return new EvidenceRequirement(stage, description, required);
        }
    }

    public record PaymentMilestoneRequest(
            @NotNull PaymentMilestoneType type,
            @NotNull @DecimalMin(value = "0.0001") @DecimalMax("1.0000") BigDecimal maximumContractRatio,
            @NotEmpty List<@NotBlank @Size(max = 64) String> requiredEvidenceCodes
    ) {
        PaymentMilestone toDomain() {
            return new PaymentMilestone(type, maximumContractRatio, requiredEvidenceCodes);
        }
    }

    public record ItemRequest(
            @NotBlank @Size(max = 40) String itemNo,
            Long buildingId,
            @Size(max = 64) String unitName,
            Long roomId,
            @NotBlank @Size(max = 240) String locationText,
            @NotBlank @Size(max = 4000) String workContent,
            @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
            @NotBlank @Size(max = 32) String unit,
            @NotNull @DecimalMin("0.00") BigDecimal estimatedUnitPrice,
            @NotNull @DecimalMin("0.00") BigDecimal estimatedAmount,
            List<@NotNull Long> linkedWorkOrderIds
    ) {
        RepairPlanDraftCommand.ItemDraft toCommand() {
            return new RepairPlanDraftCommand.ItemDraft(
                    itemNo, buildingId, unitName, roomId, locationText, workContent,
                    quantity, unit, estimatedUnitPrice, estimatedAmount,
                    linkedWorkOrderIds == null ? List.of() : linkedWorkOrderIds);
        }
    }

    public record AttachmentReferenceRequest(
            @NotNull Long attachmentId,
            @NotNull AttachmentPurpose purpose
    ) {
        RepairPlanDraftCommand.AttachmentReference toCommand() {
            return new RepairPlanDraftCommand.AttachmentReference(attachmentId, purpose);
        }
    }
}
