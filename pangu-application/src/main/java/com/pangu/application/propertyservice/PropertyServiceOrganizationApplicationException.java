// 关联业务：统一表达物业服务组织登记、企业核验和项目部启用失败原因。
package com.pangu.application.propertyservice;

import lombok.Getter;

/**
 * 物业服务组织登记应用异常。
 */
@Getter
public class PropertyServiceOrganizationApplicationException extends RuntimeException {

    private final Reason reason;

    public PropertyServiceOrganizationApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public PropertyServiceOrganizationApplicationException(Reason reason, String message, Throwable cause) {
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
        DUPLICATE_ACTIVE_ORGANIZATION,
        STORAGE_UNAVAILABLE,
        EXTERNAL_VERIFICATION_UNAVAILABLE
    }
}
