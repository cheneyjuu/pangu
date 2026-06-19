package com.pangu.application.dispute;

/**
 * 业主异议 use case 异常。仿 {@code GovernanceLockApplicationException}：内部 Reason
 * 由 web 层 {@code DisputeExceptionTranslator} 映射到 {@code DisputeErrorCode}。
 */
public class DisputeApplicationException extends RuntimeException {

    public enum Reason {
        DISPUTE_NOT_FOUND,
        DISPUTE_INVALID_TRANSITION,
        DISPUTE_NOT_OWNER,
        DISPUTE_LEVEL_EXCEEDED,
        DISPUTE_CONCURRENT_MODIFICATION,
        DECISION_DUPLICATE,
        DISPUTE_TYPE_LEVEL_MISMATCH,
        DISPUTE_ESCALATE_REQUIRES_REJECTED,
        DISPUTE_ALREADY_CLOSED,
        EVIDENCE_DISPUTE_CLOSED
    }

    private final Reason reason;

    public DisputeApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DisputeApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
