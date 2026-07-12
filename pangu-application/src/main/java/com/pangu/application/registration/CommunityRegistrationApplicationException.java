// 关联业务：统一表达小区注册、审核、材料和租户开通失败原因。
package com.pangu.application.registration;

import lombok.Getter;

/**
 * 小区注册应用异常。
 */
@Getter
public class CommunityRegistrationApplicationException extends RuntimeException {

    private final Reason reason;

    public CommunityRegistrationApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CommunityRegistrationApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        PARAM_INVALID,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        INVALID_STATUS,
        DUPLICATE_APPLICATION,
        MATERIAL_REQUIRED,
        CONCURRENT_MODIFICATION,
        PROVISIONING_FAILED,
        STORAGE_UNAVAILABLE
    }
}
