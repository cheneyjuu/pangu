package com.pangu.application.assembly;

public class OwnersAssemblyApplicationException extends RuntimeException {

    public enum Reason {
        PARAM_INVALID,
        FORBIDDEN,
        NOT_FOUND,
        INVALID_STATUS,
        NOTICE_NOT_COMPLETED,
        DELIVERY_REQUIRED,
        AUTH_LEVEL_INSUFFICIENT,
        OPID_NOT_OWNED,
        OPID_OUT_OF_SCOPE,
        VOTE_ALREADY_CAST,
        CONCURRENT_MODIFICATION
    }

    private final Reason reason;

    public OwnersAssemblyApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public OwnersAssemblyApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
