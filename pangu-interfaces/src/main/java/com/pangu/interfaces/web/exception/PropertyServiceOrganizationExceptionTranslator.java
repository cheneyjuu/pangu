// 关联业务：把物业服务组织应用异常翻译为稳定 HTTP 错误契约。
package com.pangu.interfaces.web.exception;

import com.pangu.application.propertyservice.PropertyServiceOrganizationApplicationException;

/**
 * 物业服务组织异常翻译器。
 */
public final class PropertyServiceOrganizationExceptionTranslator {

    private PropertyServiceOrganizationExceptionTranslator() {
    }

    public static PropertyServiceOrganizationErrorCode translate(
            PropertyServiceOrganizationApplicationException exception) {
        return switch (exception.getReason()) {
            case PARAM_INVALID -> PropertyServiceOrganizationErrorCode.PARAM_INVALID;
            case UNAUTHORIZED -> PropertyServiceOrganizationErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> PropertyServiceOrganizationErrorCode.FORBIDDEN;
            case NOT_FOUND -> PropertyServiceOrganizationErrorCode.NOT_FOUND;
            case INVALID_STATUS -> PropertyServiceOrganizationErrorCode.INVALID_STATUS;
            case MATERIAL_REQUIRED -> PropertyServiceOrganizationErrorCode.MATERIAL_REQUIRED;
            case CONCURRENT_MODIFICATION -> PropertyServiceOrganizationErrorCode.CONCURRENT_MODIFICATION;
            case DUPLICATE_ACTIVE_ORGANIZATION -> PropertyServiceOrganizationErrorCode.DUPLICATE_ACTIVE_ORGANIZATION;
            case STORAGE_UNAVAILABLE -> PropertyServiceOrganizationErrorCode.STORAGE_UNAVAILABLE;
            case EXTERNAL_VERIFICATION_UNAVAILABLE ->
                    PropertyServiceOrganizationErrorCode.EXTERNAL_VERIFICATION_UNAVAILABLE;
        };
    }
}
