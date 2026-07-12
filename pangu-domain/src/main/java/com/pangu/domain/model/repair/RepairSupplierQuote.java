package com.pangu.domain.model.repair;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 维修供应商报价。 */
public record RepairSupplierQuote(
        Long quoteId,
        Long workOrderId,
        Long tenantId,
        String supplierName,
        BigDecimal quoteAmount,
        String quoteSummary,
        Long attachmentId,
        String attachmentHash,
        Long submittedByUserId,
        String submittedByRoleKey,
        boolean submittedBySupplier,
        boolean supplierConfirmed,
        Long supplierDeptId,
        Long quoteInvitationId,
        RepairQuoteSubmissionSource submissionSource,
        RepairQuoteConfirmationStatus confirmationStatus,
        String originalSource,
        String originalAttachmentHash,
        RepairSupplierQuoteStatus quoteStatus,
        int revisionNo,
        Long supersededByQuoteId,
        LocalDateTime createTime
) {

    public boolean confirmedForContract() {
        return confirmationStatus != null
                ? confirmationStatus.confirmedForContract()
                : supplierConfirmed;
    }
}
