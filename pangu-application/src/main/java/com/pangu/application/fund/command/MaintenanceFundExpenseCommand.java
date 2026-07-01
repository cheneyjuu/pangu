package com.pangu.application.fund.command;

import java.math.BigDecimal;

/**
 * 维修资金支取出账命令。
 */
public record MaintenanceFundExpenseCommand(
        Long tenantId,
        Long accountId,
        BigDecimal amount,
        Long businessRefId,
        Long operatorId) {
}
