package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SubmitRepairPlanRequest(
        @DecimalMin(value = "0.01") BigDecimal planBudget,
        @DecimalMin(value = "0.01") BigDecimal publicCeilingPrice,
        @Size(max = 64) String fundSource,
        @Size(max = 500) String remark
) {
}
