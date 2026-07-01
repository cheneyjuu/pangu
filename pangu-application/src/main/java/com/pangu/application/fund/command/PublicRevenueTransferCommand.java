package com.pangu.application.fund.command;

import java.math.BigDecimal;

/**
 * 公共收益划拨入维修资金账户命令。
 */
public record PublicRevenueTransferCommand(
        Long tenantId,
        Long accountId,
        BigDecimal amount,
        Long businessRefId,
        Long operatorId) {
}
