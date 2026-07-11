package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record SubmitRepairSupplierQuoteCommand(
        Long supplierDeptId,
        Long quoteInvitationId,
        String supplierName,
        BigDecimal quoteAmount,
        String quoteSummary,
        String attachmentHash,
        String originalSource,
        String originalAttachmentHash,
        String confirmationStatus,
        String remark
) {
}
