package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record CreateRepairPaymentRequestCommand(
        String milestoneType,
        BigDecimal requestedAmount,
        String conditionEvidenceHash,
        String remark
) {
}
