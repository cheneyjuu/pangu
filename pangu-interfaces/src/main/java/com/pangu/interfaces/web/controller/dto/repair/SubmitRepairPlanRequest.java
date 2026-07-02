package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SubmitRepairPlanRequest(
        @Size(max = 2000) String surveySummary,
        @Size(max = 32) String riskLevel,
        @DecimalMin("0.00") BigDecimal planBudget,
        @Size(max = 64) String fundSource,
        @Size(max = 500) String remark
) {
}
