package com.pangu.interfaces.web.exception;

import com.pangu.application.repair.SupplierActivationApplicationException;

public final class SupplierActivationExceptionTranslator {

    private SupplierActivationExceptionTranslator() {
    }

    public static SupplierActivationErrorCode translate(SupplierActivationApplicationException ex) {
        return switch (ex.reason()) {
            case PARAM_INVALID -> SupplierActivationErrorCode.PARAM_INVALID;
            case FORBIDDEN -> SupplierActivationErrorCode.FORBIDDEN;
            case INVITATION_NOT_FOUND -> SupplierActivationErrorCode.INVITATION_NOT_FOUND;
            case INVITATION_UNAVAILABLE -> SupplierActivationErrorCode.INVITATION_UNAVAILABLE;
            case INVITATION_EXPIRED -> SupplierActivationErrorCode.INVITATION_EXPIRED;
            case SMS_CODE_INVALID -> SupplierActivationErrorCode.SMS_CODE_INVALID;
            case ACCOUNT_DISABLED -> SupplierActivationErrorCode.ACCOUNT_DISABLED;
            case IDENTITY_CONFLICT -> SupplierActivationErrorCode.IDENTITY_CONFLICT;
            case SYSTEM_CONFIG_ERROR -> SupplierActivationErrorCode.SYSTEM_CONFIG_ERROR;
        };
    }
}
