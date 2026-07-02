package com.pangu.application.admin;

import lombok.Getter;

/**
 * 管理端工作身份授权业务异常。
 */
@Getter
public class WorkIdentityApplicationException extends RuntimeException {

    private final Reason reason;

    public WorkIdentityApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public WorkIdentityApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        PARAM_INVALID,
        FORBIDDEN,
        ACCOUNT_NOT_FOUND,
        ROLE_NOT_FOUND,
        DEPT_NOT_FOUND,
        DUPLICATE_IDENTITY,
        ROLE_DEPT_MISMATCH,
        BUILDING_REQUIRED,
        BUILDING_NOT_ALLOWED,
        ROLE_BINDING_INCONSISTENT
    }
}
