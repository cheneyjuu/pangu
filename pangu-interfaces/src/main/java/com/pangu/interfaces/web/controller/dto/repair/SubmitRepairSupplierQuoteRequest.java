package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SubmitRepairSupplierQuoteRequest(
        Long supplierDeptId,
        Long quoteInvitationId,
        @Size(max = 120) String supplierName,
        @NotNull @DecimalMin("0.00") BigDecimal quoteAmount,
        @Size(max = 2000) String quoteSummary,
        @NotBlank @Size(max = 128) String attachmentHash,
        @Size(max = 32) String originalSource,
        @Size(max = 128) String originalAttachmentHash,
        @Size(max = 40) String confirmationStatus,
        @Size(max = 500) String remark
) {
}
