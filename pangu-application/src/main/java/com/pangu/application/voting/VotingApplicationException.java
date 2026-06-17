package com.pangu.application.voting;

/**
 * 投票结算应用层异常。
 *
 * <p>映射到 web 层 {@code ElectionErrorCode} 由 Phase 6 完成；本期作为 application
 * 与 web 之间的契约，使用强类型 {@link Reason} 表达失败语义。
 */
public class VotingApplicationException extends RuntimeException {

    public enum Reason {
        SUBJECT_NOT_FOUND,
        SUBJECT_NOT_VOTING,
        SUBJECT_ALREADY_SETTLED,
        DENOMINATOR_RESOLVE_FAILED,
        SUBJECT_TYPE_NOT_SUPPORTED,
        ATTESTATION_FAILED,
        CONCURRENT_SETTLEMENT
    }

    private final Reason reason;

    public VotingApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public VotingApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
