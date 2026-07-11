package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record SubmitRepairSupplierQuoteCommand(
        Long supplierDeptId,
        Long quoteInvitationId,
        String supplierName,
        BigDecimal quoteAmount,
        String quoteSummary,
        Long attachmentId,
        String originalSource,
        String confirmationStatus,
        String remark
) {
}
