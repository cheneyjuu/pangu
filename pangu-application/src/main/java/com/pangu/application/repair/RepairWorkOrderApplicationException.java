// 关联业务：统一表达维修工单、供应商核验及外部服务调用中的应用层失败原因。
package com.pangu.application.repair;

public class RepairWorkOrderApplicationException extends RuntimeException {

    public enum Reason {
        PARAM_INVALID,
        FORBIDDEN,
        NOT_FOUND,
        PROPERTY_NOT_OWNED,
        BUILDING_NOT_IN_SCOPE,
        INVALID_STATUS,
        LOCATION_NOT_VERIFIED,
        HANDOVER_LOCKED,
        STORAGE_UNAVAILABLE,
        EXTERNAL_VERIFICATION_UNAVAILABLE
    }

    private final Reason reason;

    public RepairWorkOrderApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RepairWorkOrderApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
