package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record SubmitRepairPlanCommand(
        BigDecimal planBudget,
        String fundSource,
        String remark
) {
}
