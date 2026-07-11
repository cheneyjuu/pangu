package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateRepairPaymentRequest(
        @NotBlank @Size(max = 24) String milestoneType,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal requestedAmount,
        @NotBlank @Size(max = 128) String conditionEvidenceHash,
        @Size(max = 500) String remark
) {
}
