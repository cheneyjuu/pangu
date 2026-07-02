package com.pangu.interfaces.web.exception;

import com.pangu.application.admin.WorkIdentityApplicationException;

/**
 * 工作身份授权异常翻译器。
 */
public final class WorkIdentityExceptionTranslator {

    private WorkIdentityExceptionTranslator() {
    }

    public static WorkIdentityErrorCode translate(WorkIdentityApplicationException ex) {
        return switch (ex.getReason()) {
            case PARAM_INVALID -> WorkIdentityErrorCode.PARAM_INVALID;
            case FORBIDDEN -> WorkIdentityErrorCode.FORBIDDEN;
            case ACCOUNT_NOT_FOUND -> WorkIdentityErrorCode.ACCOUNT_NOT_FOUND;
            case ROLE_NOT_FOUND -> WorkIdentityErrorCode.ROLE_NOT_FOUND;
            case DEPT_NOT_FOUND -> WorkIdentityErrorCode.DEPT_NOT_FOUND;
            case DUPLICATE_IDENTITY -> WorkIdentityErrorCode.DUPLICATE_IDENTITY;
            case ROLE_DEPT_MISMATCH -> WorkIdentityErrorCode.ROLE_DEPT_MISMATCH;
            case BUILDING_REQUIRED -> WorkIdentityErrorCode.BUILDING_REQUIRED;
            case BUILDING_NOT_ALLOWED -> WorkIdentityErrorCode.BUILDING_NOT_ALLOWED;
            case ROLE_BINDING_INCONSISTENT -> WorkIdentityErrorCode.ROLE_BINDING_INCONSISTENT;
        };
    }
}
