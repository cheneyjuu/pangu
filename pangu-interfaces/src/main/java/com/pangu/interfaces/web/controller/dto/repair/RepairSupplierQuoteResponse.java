package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairSupplierQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RepairSupplierQuoteResponse(
        Long quoteId,
        Long supplierDeptId,
        String supplierName,
        BigDecimal quoteAmount,
        String quoteSummary,
        String submissionSource,
        String confirmationStatus,
        LocalDateTime createTime
) {
    public static RepairSupplierQuoteResponse from(RepairSupplierQuote quote) {
        return new RepairSupplierQuoteResponse(
                quote.quoteId(),
                quote.supplierDeptId(),
                quote.supplierName(),
                quote.quoteAmount(),
                quote.quoteSummary(),
                quote.submissionSource() == null ? null : quote.submissionSource().name(),
                quote.confirmationStatus() == null ? null : quote.confirmationStatus().name(),
                quote.createTime());
    }
}
