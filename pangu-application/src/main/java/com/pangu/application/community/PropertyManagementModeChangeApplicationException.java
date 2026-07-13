// 关联业务：统一表达物业管理模式变更申请、审核和执行的业务失败原因。
package com.pangu.application.community;

import lombok.Getter;

/**
 * 物业管理模式变更应用异常。
 */
@Getter
public class PropertyManagementModeChangeApplicationException extends RuntimeException {

    private final Reason reason;

    public PropertyManagementModeChangeApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PropertyManagementModeChangeApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        PARAM_INVALID,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        INVALID_STATUS,
        MATERIAL_REQUIRED,
        CONCURRENT_MODIFICATION,
        DUPLICATE_ACTIVE_REQUEST,
        STORAGE_UNAVAILABLE
    }
}
