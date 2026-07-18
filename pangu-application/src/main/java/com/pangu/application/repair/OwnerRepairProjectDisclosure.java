// 关联业务：向业主端披露已锁定维修工程方案、维修点位和可信资金切片摘要，同时隐藏人员身份和逐户分摊明细。
package com.pangu.application.repair;

import com.pangu.domain.model.repair.RepairProject;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule;
import com.pangu.domain.model.repair.RepairProject.AttachmentPurpose;
import com.pangu.domain.model.repair.RepairProject.FundSource;
import com.pangu.domain.model.repair.RepairProject.FundingSourceType;
import com.pangu.domain.model.repair.RepairProject.GovernancePath;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import com.pangu.domain.model.repair.RepairProject.Status;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineType;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;
import com.pangu.domain.model.repair.RepairWorkflowType;

import java.math.BigDecimal;
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
            RepairSupplierSelectionMethod supplierSelectionMethod,
            String supplierSelectionReason,
            PublishedSupplierSelection selectedSupplier,
            List<PublishedWorkPoint> workPoints,
            List<PublishedFundingSlice> fundingSlices,
            List<PublishedAttachment> attachments,
            LocalDateTime lockedAt
    ) {
        public PublishedPlan {
            workPoints = workPoints == null ? List.of() : List.copyOf(workPoints);
            fundingSlices = fundingSlices == null ? List.of() : List.copyOf(fundingSlices);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    public record PublishedSupplierSelection(
            Long quoteId,
            Long supplierDeptId,
            String supplierName,
            BigDecimal amountExcludingTax,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal quoteAmount,
            String quoteSummary,
            Long quoteAttachmentId,
            Integer constructionPeriodDays,
            Integer warrantyDays,
            RepairSupplierSelectionMethod selectionMethod,
            String recommendationReason,
            String insufficientQuoteReason,
            List<PublishedQuoteLine> quoteLines
    ) {
        public PublishedSupplierSelection {
            quoteLines = quoteLines == null ? List.of() : List.copyOf(quoteLines);
        }
    }

    /** 向受影响业主披露中选报价的材料、人工、运输等构成，不披露供应商内部信息。 */
    public record PublishedQuoteLine(
            Long workPointId,
            String workPointName,
            Integer lineNo,
            String itemName,
            QuoteLineType lineType,
            String workDescription,
            String specificationModel,
            String brand,
            String procurementMethod,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPriceExcludingTax,
            BigDecimal amountExcludingTax,
            BigDecimal taxIncludedUnitPrice,
            BigDecimal taxRate,
            BigDecimal taxIncludedAmount,
            String remark
    ) {
    }

    public record PublishedWorkPoint(
            Long workPointId,
            String businessName,
            Long buildingId,
            String unitName,
            RepairProject.WorkPointLocationType locationType,
            Long referenceRoomId,
            String commonAreaName,
            String spaceName,
            String orientation,
            String component,
            String specificPart,
            String symptom,
            RepairProject.WorkPointCauseStatus causeStatus,
            String causeBasis,
            String proposedMeasure,
            String technicalRequirements,
            BigDecimal quantity,
            String unit,
            BigDecimal preliminaryEstimatedAmount,
            String estimateSource
    ) {
    }

    public record PublishedFundingSlice(
            FundingSourceType sourceType,
            BigDecimal approvedAmount
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
