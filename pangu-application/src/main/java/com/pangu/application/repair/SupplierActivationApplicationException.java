package com.pangu.application.repair;

public class SupplierActivationApplicationException extends RuntimeException {

    public enum Reason {
        PARAM_INVALID,
        FORBIDDEN,
        INVITATION_NOT_FOUND,
        INVITATION_UNAVAILABLE,
        INVITATION_EXPIRED,
        SMS_CODE_INVALID,
        ACCOUNT_DISABLED,
        IDENTITY_CONFLICT,
        SYSTEM_CONFIG_ERROR
    }

    private final Reason reason;

    public SupplierActivationApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public SupplierActivationApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
