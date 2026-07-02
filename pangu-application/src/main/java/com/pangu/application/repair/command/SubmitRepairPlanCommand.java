package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record SubmitRepairPlanCommand(
        String surveySummary,
        String riskLevel,
        BigDecimal planBudget,
        String fundSource,
        String remark
) {
}
