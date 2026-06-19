package com.pangu.interfaces.web.exception;

import com.pangu.application.dispute.DisputeApplicationException;

/**
 * {@link DisputeApplicationException} → web 层 {@link DisputeErrorCode} 翻译器。
 */
public final class DisputeExceptionTranslator {

    private DisputeExceptionTranslator() {
    }

    public static DisputeErrorCode translate(DisputeApplicationException ex) {
        return switch (ex.getReason()) {
            case DISPUTE_NOT_FOUND -> DisputeErrorCode.DISPUTE_NOT_FOUND;
            case DISPUTE_INVALID_TRANSITION -> DisputeErrorCode.DISPUTE_INVALID_TRANSITION;
            case DISPUTE_NOT_OWNER -> DisputeErrorCode.DISPUTE_NOT_OWNER;
            case DISPUTE_LEVEL_EXCEEDED -> DisputeErrorCode.DISPUTE_LEVEL_EXCEEDED;
            case DISPUTE_CONCURRENT_MODIFICATION -> DisputeErrorCode.DISPUTE_CONCURRENT_MODIFICATION;
            case DECISION_DUPLICATE -> DisputeErrorCode.DECISION_DUPLICATE;
            case DISPUTE_TYPE_LEVEL_MISMATCH -> DisputeErrorCode.DISPUTE_TYPE_LEVEL_MISMATCH;
            case DISPUTE_ESCALATE_REQUIRES_REJECTED -> DisputeErrorCode.DISPUTE_ESCALATE_REQUIRES_REJECTED;
            case DISPUTE_ALREADY_CLOSED -> DisputeErrorCode.DISPUTE_ALREADY_CLOSED;
            case EVIDENCE_DISPUTE_CLOSED -> DisputeErrorCode.EVIDENCE_DISPUTE_CLOSED;
        };
    }
}
