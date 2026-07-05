package com.pangu.application.owner;

import lombok.Getter;

@Getter
public class PropertyBindingApplicationException extends RuntimeException {

    private final Reason reason;

    public PropertyBindingApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PropertyBindingApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        PARAM_INVALID,
        FORBIDDEN,
        UNAUTHORIZED,
        NOT_FOUND,
        BAD_REQUEST,
        SYSTEM_ERROR
    }
}
