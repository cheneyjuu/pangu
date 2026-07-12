// 关联业务：把小区注册应用异常翻译为稳定 HTTP 错误契约。
package com.pangu.interfaces.web.exception;

import com.pangu.application.registration.CommunityRegistrationApplicationException;

/**
 * 小区注册异常翻译器。
 */
public final class CommunityRegistrationExceptionTranslator {

    private CommunityRegistrationExceptionTranslator() {
    }

    public static CommunityRegistrationErrorCode translate(
            CommunityRegistrationApplicationException exception) {
        return switch (exception.getReason()) {
            case PARAM_INVALID -> CommunityRegistrationErrorCode.PARAM_INVALID;
            case UNAUTHORIZED -> CommunityRegistrationErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> CommunityRegistrationErrorCode.FORBIDDEN;
            case NOT_FOUND -> CommunityRegistrationErrorCode.NOT_FOUND;
            case INVALID_STATUS -> CommunityRegistrationErrorCode.INVALID_STATUS;
            case DUPLICATE_APPLICATION -> CommunityRegistrationErrorCode.DUPLICATE_APPLICATION;
            case MATERIAL_REQUIRED -> CommunityRegistrationErrorCode.MATERIAL_REQUIRED;
            case CONCURRENT_MODIFICATION -> CommunityRegistrationErrorCode.CONCURRENT_MODIFICATION;
            case PROVISIONING_FAILED -> CommunityRegistrationErrorCode.PROVISIONING_FAILED;
            case STORAGE_UNAVAILABLE -> CommunityRegistrationErrorCode.STORAGE_UNAVAILABLE;
        };
    }
}
