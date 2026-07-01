package com.pangu.application.fund.command;

import java.math.BigDecimal;

/**
 * 信托制双签动账出账命令。
 */
public record TrustFundDisbursementCommand(
        Long tenantId,
        Long accountId,
        Long trustPaymentId,
        Integer installmentNo,
        Long previousTrustPaymentId,
        BigDecimal amount,
        Long operatorId) {

    public TrustFundDisbursementCommand(
            Long tenantId,
            Long accountId,
            Long trustPaymentId,
            BigDecimal amount,
            Long operatorId) {
        this(tenantId, accountId, trustPaymentId, 1, null, amount, operatorId);
    }
}
