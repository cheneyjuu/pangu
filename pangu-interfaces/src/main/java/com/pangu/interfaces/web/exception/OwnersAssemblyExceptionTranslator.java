package com.pangu.interfaces.web.exception;

import com.pangu.application.assembly.OwnersAssemblyApplicationException;

public final class OwnersAssemblyExceptionTranslator {

    private OwnersAssemblyExceptionTranslator() {
    }

    public static OwnersAssemblyErrorCode translate(OwnersAssemblyApplicationException ex) {
        return switch (ex.reason()) {
            case PARAM_INVALID -> OwnersAssemblyErrorCode.PARAM_INVALID;
            case FORBIDDEN -> OwnersAssemblyErrorCode.FORBIDDEN;
            case NOT_FOUND -> OwnersAssemblyErrorCode.NOT_FOUND;
            case INVALID_STATUS -> OwnersAssemblyErrorCode.INVALID_STATUS;
            case NOTICE_NOT_COMPLETED -> OwnersAssemblyErrorCode.NOTICE_NOT_COMPLETED;
            case DELIVERY_REQUIRED -> OwnersAssemblyErrorCode.DELIVERY_REQUIRED;
            case AUTH_LEVEL_INSUFFICIENT -> OwnersAssemblyErrorCode.AUTH_LEVEL_INSUFFICIENT;
            case OPID_NOT_OWNED -> OwnersAssemblyErrorCode.OPID_NOT_OWNED;
            case OPID_OUT_OF_SCOPE -> OwnersAssemblyErrorCode.OPID_OUT_OF_SCOPE;
            case VOTE_ALREADY_CAST -> OwnersAssemblyErrorCode.VOTE_ALREADY_CAST;
            case CONCURRENT_MODIFICATION -> OwnersAssemblyErrorCode.CONCURRENT_MODIFICATION;
        };
    }
}
