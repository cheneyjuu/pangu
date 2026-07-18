// 关联业务：表达维修工程项目方案级邀价、报价版本和中选供应商快照。
package com.pangu.domain.model.repair;

import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;

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

    /**
     * 定商授权的可用性由决定、用印和规则快照共同决定，不能由询价草稿或前端状态推导。
     */
    public enum SelectionAuthorizationStatus {
        PENDING_AUTHORIZATION,
        AUTHORIZED,
        UNSUPPORTED_WORKFLOW
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

    /** 报价明细类别决定录入时展示哪些业务字段；运输、清运等综合费用可以不直接对应某一个维修点位。 */
    public enum QuoteLineType {
        MATERIAL_EQUIPMENT,
        LABOR_SERVICE,
        CONSTRUCTION_MEASURE,
        TRANSPORT_CLEANUP,
        OTHER
    }

    /**
     * 报价明细可引用维修点位，但报价行不是维修点位的替代物，也不要求一一覆盖。
     */
    public record QuoteLine(
            Long quoteLineId,
            Long quoteId,
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
            String remark
    ) {
    }

    /** 供应商或物业代录时提交的未计价报价行。 */
    public record QuoteLineDraft(
            Long workPointId,
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
            SupplierSelectionEvaluationRule selectionEvaluationRule,
            String selectionRationale,
            Long selectionEvidenceAttachmentId,
            Long governanceBasisId,
            String governanceBasisHash,
            Long frameworkRelationId,
            Long confirmedByUserId,
            LocalDateTime createTime
    ) {
    }

    /**
     * 管理端只读取已经封存的授权事实；当前操作者是否可确认由服务端按原审批人和现任职务计算。
     */
    public record SelectionAuthorization(
            SelectionAuthorizationStatus status,
            String blockingReason,
            RepairSupplierSelectionMethod approvedSelectionMethod,
            SupplierSelectionEvaluationRule approvedEvaluationRule,
            Integer minimumInvitedSupplierCount,
            Integer minimumValidQuoteCount,
            String nonCompetitiveSelectionBasis,
            BigDecimal approvedBudgetAmount,
            Long governanceBasisId,
            String governanceBasisHash,
            Long buildingProcessId,
            Long decisionId,
            boolean currentActorMayConfirm
    ) {
    }

    public record Details(
            Long projectId,
            Long planId,
            RepairSupplierSelectionMethod selectionMethod,
            SelectionAuthorization selectionAuthorization,
            List<RepairFrameworkRelation> eligibleFrameworkRelations,
            List<Invitation> invitations,
            List<Quote> quotes,
            Selection selection
    ) {
        public Details {
            eligibleFrameworkRelations = eligibleFrameworkRelations == null
                    ? List.of() : List.copyOf(eligibleFrameworkRelations);
            invitations = invitations == null ? List.of() : List.copyOf(invitations);
            quotes = quotes == null ? List.of() : List.copyOf(quotes);
        }
    }
}
