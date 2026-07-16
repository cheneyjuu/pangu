// 关联业务：接收维修工程结构化实施方案草稿、工程项、付款节点与附件用途引用。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule;
import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.EvidenceRequirement;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestone;
import com.pangu.domain.model.repair.RepairProject.SettlementMethod;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RepairPlanDraftCommand(
        String problemCause,
        String implementationScope,
        BigDecimal budgetTotal,
        RepairSupplierSelectionMethod supplierSelectionMethod,
        String supplierSelectionReason,
        String constructionManagementRequirements,
        List<EvidenceRequirement> evidenceRequirements,
        String safetyRequirements,
        String acceptanceMethod,
        String affectedOwnerScopeDescription,
        Integer minimumAffectedOwnerAcceptors,
        AffectedOwnerPassRule affectedOwnerPassRule,
        BigDecimal affectedOwnerApprovalRatio,
        SettlementMethod settlementMethod,
        LocalDate plannedStartDate,
        LocalDate plannedCompletionDate,
        Integer warrantyDays,
        boolean priceReviewRequired,
        List<PaymentMilestone> paymentMilestones,
        List<ItemDraft> items,
        List<AttachmentReference> attachments
) {
    public RepairPlanDraftCommand {
        evidenceRequirements = evidenceRequirements == null ? List.of() : List.copyOf(evidenceRequirements);
        paymentMilestones = paymentMilestones == null ? List.of() : List.copyOf(paymentMilestones);
        items = items == null ? List.of() : List.copyOf(items);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public record ItemDraft(
            String itemNo,
            Long buildingId,
            String unitName,
            Long roomId,
            String locationText,
            String workContent,
            BigDecimal quantity,
            String unit,
            BigDecimal estimatedUnitPrice,
            BigDecimal estimatedAmount,
            List<Long> linkedWorkOrderIds
    ) {
        public ItemDraft {
            linkedWorkOrderIds = linkedWorkOrderIds == null ? List.of() : List.copyOf(linkedWorkOrderIds);
        }
    }

    public record AttachmentReference(
            Long attachmentId,
            AttachmentPurpose purpose
    ) {
    }
}
