package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReviewRepairPriceRequest(
        @NotBlank @Size(max = 32) String reviewMode,
        @NotNull @DecimalMin("0.00") BigDecimal reviewedAmount,
        @Size(max = 128) String reviewReportHash,
        @NotBlank @Size(max = 32) String conclusion,
        @Size(max = 1000) String opinion
) {
}
