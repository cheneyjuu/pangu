// 关联业务：向业主端披露已锁定维修工程方案，同时隐藏人员身份和逐户分摊明细。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule;
import com.pangu.domain.model.repair.RepairProject.AllocationRuleType;
import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.EvidenceRequirement;
import com.pangu.domain.model.repair.RepairProject.FundSource;
import com.pangu.domain.model.repair.RepairProject.GovernancePath;
import com.pangu.domain.model.repair.RepairProject.PaymentMilestone;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import com.pangu.domain.model.repair.RepairProject.SettlementMethod;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.model.repair.RepairWorkflowType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OwnerRepairProjectDisclosure(
        Long workOrderId,
        Long projectId,
        String projectNo,
        String projectName,
        RepairWorkflowType workflowType,
        ScopeType scopeType,
        Long buildingId,
        String unitName,
        FundSource fundSource,
        GovernancePath governancePath,
        Status status,
        PublishedPlan plan
) {

    public record PublishedPlan(
            Long planId,
            Integer versionNo,
            String planDescription,
            BigDecimal budgetTotal,
            AllocationRuleType allocationRuleType,
            String allocationRuleDescription,
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
            List<PublishedItem> items,
            AllocationSummary allocationSummary,
            List<PublishedAttachment> attachments,
            LocalDateTime lockedAt
    ) {
        public PublishedPlan {
            evidenceRequirements = List.copyOf(evidenceRequirements);
            paymentMilestones = List.copyOf(paymentMilestones);
            items = List.copyOf(items);
            attachments = List.copyOf(attachments);
        }
    }

    public record PublishedItem(
            Long itemId,
            String itemNo,
            String locationText,
            String workContent,
            BigDecimal quantity,
            String unit,
            BigDecimal estimatedUnitPrice,
            BigDecimal estimatedAmount
    ) {
    }

    public record AllocationSummary(
            long roomCount,
            long ownerCount,
            BigDecimal totalBuildArea
    ) {
    }

    public record PublishedAttachment(
            Long attachmentId,
            AttachmentPurpose purpose,
            String originalFileName,
            String contentType,
            Long fileSize
    ) {
    }
}
