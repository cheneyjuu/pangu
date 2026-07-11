package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record ReviewRepairPriceCommand(
        String reviewMode,
        BigDecimal reviewedAmount,
        String reviewReportHash,
        String conclusion,
        String opinion
) {
}
