package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record SubmitRepairPlanCommand(
        BigDecimal planBudget,
        BigDecimal publicCeilingPrice,
        String fundSource,
        String remark
) {
}
