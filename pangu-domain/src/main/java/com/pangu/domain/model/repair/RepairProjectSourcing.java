// 关联业务：表达维修工程项目方案级邀价、报价版本和中选供应商快照。
package com.pangu.domain.model.repair;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RepairProjectSourcing {

    private RepairProjectSourcing() {
    }

    public enum InvitationStatus {
        PENDING,
        SUBMITTED,
        DECLINED,
        EXPIRED,
        CANCELLED
    }

    public enum InvitationType {
        INITIAL,
        REVISION
    }

    public record Invitation(
            Long invitationId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long supplierDeptId,
            String supplierName,
            Long invitedByUserId,
            LocalDateTime deadline,
            InvitationStatus status,
            Integer invitationRound,
            InvitationType invitationType,
            String revisionReason,
            LocalDateTime sentAt,
            LocalDateTime respondedAt
    ) {
    }

    public record Quote(
            Long quoteId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long supplierDeptId,
            String supplierName,
            BigDecimal amountExcludingTax,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal quoteAmount,
            String quoteSummary,
            Long attachmentId,
            String attachmentHash,
            Long submittedByUserId,
            String submittedByRoleKey,
            RepairQuoteSubmissionSource submissionSource,
            RepairQuoteConfirmationStatus confirmationStatus,
            String originalSource,
            Integer constructionPeriodDays,
            Integer warrantyDays,
            boolean originalAmountConfirmed,
            RepairSupplierQuoteStatus quoteStatus,
            Integer revisionNo,
            Long supersededByQuoteId,
            LocalDateTime createTime,
            List<QuoteLine> quoteLines
    ) {
        public Quote {
            quoteLines = quoteLines == null ? List.of() : List.copyOf(quoteLines);
        }
    }

    /** 报价明细类别决定录入时展示哪些业务字段，不改变其必须绑定方案工程项的边界。 */
    public enum QuoteLineType {
        MATERIAL_EQUIPMENT,
        LABOR_SERVICE,
        CONSTRUCTION_MEASURE,
        TRANSPORT_CLEANUP,
        OTHER
    }

    /**
     * 报价明细绑定锁定工程项，但允许供应商在工程项下拆分材料、人工、运输等构成。
     * 工程范围仍由 projectItemId 指向的方案工程项决定，报价行不能新增方案外范围。
     */
    public record QuoteLine(
            Long quoteLineId,
            Long quoteId,
            Long projectItemId,
            String projectItemNo,
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
            String remark
    ) {
    }

    /** 供应商或物业代录时提交的未计价报价行。 */
    public record QuoteLineDraft(
            Long projectItemId,
            String itemName,
            QuoteLineType lineType,
            String workDescription,
            String specificationModel,
            String brand,
            String procurementMethod,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPriceExcludingTax,
            String remark
    ) {
    }

    public record Selection(
            Long selectionId,
            Long projectId,
            Long planId,
            Long tenantId,
            Long quoteId,
            Long supplierDeptId,
            String supplierName,
            BigDecimal quoteAmount,
            RepairSupplierSelectionMethod selectionMethod,
            String recommendationReason,
            String insufficientQuoteReason,
            Long frameworkRelationId,
            Long recommendedByUserId,
            LocalDateTime createTime
    ) {
    }

    public record Details(
            Long projectId,
            Long planId,
            RepairSupplierSelectionMethod selectionMethod,
            List<Invitation> invitations,
            List<Quote> quotes,
            Selection selection
    ) {
        public Details {
            invitations = invitations == null ? List.of() : List.copyOf(invitations);
            quotes = quotes == null ? List.of() : List.copyOf(quotes);
        }
    }
}
