// 关联业务：把物业管理模式变更应用异常翻译为稳定 HTTP 错误契约。
package com.pangu.interfaces.web.exception;

import com.pangu.application.community.PropertyManagementModeChangeApplicationException;

/**
 * 物业管理模式变更异常翻译器。
 */
public final class PropertyManagementModeChangeExceptionTranslator {

    private PropertyManagementModeChangeExceptionTranslator() {
    }

    public static PropertyManagementModeChangeErrorCode translate(
            PropertyManagementModeChangeApplicationException exception) {
        return switch (exception.getReason()) {
            case PARAM_INVALID -> PropertyManagementModeChangeErrorCode.PARAM_INVALID;
            case UNAUTHORIZED -> PropertyManagementModeChangeErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> PropertyManagementModeChangeErrorCode.FORBIDDEN;
            case NOT_FOUND -> PropertyManagementModeChangeErrorCode.NOT_FOUND;
            case INVALID_STATUS -> PropertyManagementModeChangeErrorCode.INVALID_STATUS;
            case MATERIAL_REQUIRED -> PropertyManagementModeChangeErrorCode.MATERIAL_REQUIRED;
            case CONCURRENT_MODIFICATION -> PropertyManagementModeChangeErrorCode.CONCURRENT_MODIFICATION;
            case DUPLICATE_ACTIVE_REQUEST -> PropertyManagementModeChangeErrorCode.DUPLICATE_ACTIVE_REQUEST;
            case STORAGE_UNAVAILABLE -> PropertyManagementModeChangeErrorCode.STORAGE_UNAVAILABLE;
        };
    }
}
