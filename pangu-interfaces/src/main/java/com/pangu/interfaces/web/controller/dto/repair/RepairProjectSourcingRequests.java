// 关联业务：校验维修工程项目邀价、报价修订、报价提交和中选供应商请求。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.RepairProjectSourcingCommands;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineType;
import com.pangu.domain.model.repair.RepairProjectSourcing.QuoteLineDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RepairProjectSourcingRequests {

    private RepairProjectSourcingRequests() {
    }

    public record InviteRequest(
            @NotEmpty @Size(max = 20) List<@NotNull Long> supplierDeptIds,
            LocalDateTime deadline
    ) {
        public RepairProjectSourcingCommands.InviteSuppliers toCommand() {
            return new RepairProjectSourcingCommands.InviteSuppliers(supplierDeptIds, deadline);
        }
    }

    public record RevisionRequest(
            @NotEmpty @Size(max = 20) List<@NotNull Long> supplierDeptIds,
            LocalDateTime deadline,
            @NotBlank @Size(max = 500) String revisionReason
    ) {
        public RepairProjectSourcingCommands.RequestQuoteRevisions toCommand() {
            return new RepairProjectSourcingCommands.RequestQuoteRevisions(
                    supplierDeptIds, deadline, revisionReason);
        }
    }

    public record SubmitQuoteRequest(
            Long supplierDeptId,
            Long invitationId,
            @NotNull @DecimalMin(value = "0.01") BigDecimal quoteAmount,
            @NotNull @DecimalMin(value = "0.000") @DecimalMax(value = "100.000") BigDecimal taxRate,
            @Size(max = 4000) String quoteSummary,
            @NotNull Long attachmentId,
            @Size(max = 40) String confirmationStatus,
            @Size(max = 32) String originalSource,
            @NotNull @Positive Integer constructionPeriodDays,
            @NotNull @PositiveOrZero Integer warrantyDays,
            @AssertTrue boolean originalAmountConfirmed,
            @NotEmpty @Size(max = 200) List<@Valid QuoteLineRequest> quoteLines
    ) {
        public RepairProjectSourcingCommands.SubmitQuote toCommand() {
            return new RepairProjectSourcingCommands.SubmitQuote(
                    supplierDeptId, invitationId, quoteAmount, taxRate, quoteSummary,
                    attachmentId, confirmationStatus, originalSource,
                    constructionPeriodDays, warrantyDays, originalAmountConfirmed,
                    quoteLines.stream().map(QuoteLineRequest::toDraft).toList());
        }
    }

    public record QuoteLineRequest(
            @NotNull Long projectItemId,
            @NotBlank @Size(max = 200) String itemName,
            @NotNull QuoteLineType lineType,
            @Size(max = 1000) String workDescription,
            @Size(max = 200) String specificationModel,
            @Size(max = 120) String brand,
            @Size(max = 120) String procurementMethod,
            @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
            @NotBlank @Size(max = 40) String unit,
            @NotNull @DecimalMin(value = "0.00") BigDecimal unitPriceExcludingTax,
            @Size(max = 500) String remark
    ) {
        public QuoteLineDraft toDraft() {
            return new QuoteLineDraft(
                    projectItemId, itemName, lineType, workDescription,
                    specificationModel, brand, procurementMethod, quantity,
                    unit, unitPriceExcludingTax, remark);
        }
    }

    public record SelectQuoteRequest(
            @NotNull Long quoteId,
            @Size(max = 1000) String recommendationReason,
            @Size(max = 1000) String insufficientQuoteReason,
            Long frameworkRelationId
    ) {
        public RepairProjectSourcingCommands.SelectQuote toCommand() {
            return new RepairProjectSourcingCommands.SelectQuote(
                    quoteId, recommendationReason, insufficientQuoteReason, frameworkRelationId);
        }
    }
}
