package com.pangu.application.fund;

/**
 * 维修资金动账应用层异常。
 */
public class MaintenanceFundApplicationException extends RuntimeException {

    public enum Reason {
        ACCOUNT_NOT_FOUND,
        TENANT_MISMATCH,
        AMOUNT_INVALID,
        INSUFFICIENT_AVAILABLE_BALANCE,
        HANDOVER_LOCKED_LARGE_AMOUNT,
        TRUST_PAYMENT_NOT_FULLY_UNLOCKED,
        TRUST_PREVIOUS_INSTALLMENT_NOT_CONFIRMED,
        CONCURRENT_MODIFICATION
    }

    private final Reason reason;

    public MaintenanceFundApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
