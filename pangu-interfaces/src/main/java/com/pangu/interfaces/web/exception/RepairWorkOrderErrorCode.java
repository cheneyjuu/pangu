package com.pangu.interfaces.web.exception;

public enum RepairWorkOrderErrorCode implements ErrorCode {

    PARAM_INVALID(42501, "报修参数非法", 400, ErrorType.BIZ, false),
    FORBIDDEN(42502, "当前身份无权操作该报修工单", 403, ErrorType.BIZ, false),
    NOT_FOUND(42503, "报修工单不存在或不可见", 404, ErrorType.BIZ, false),
    PROPERTY_NOT_OWNED(42504, "房产不属于当前业主或未完成核销", 403, ErrorType.BIZ, false),
    BUILDING_NOT_IN_SCOPE(42505, "楼栋不在当前小区范围内", 403, ErrorType.BIZ, false),
    INVALID_STATUS(42506, "当前工单状态不允许该动作", 409, ErrorType.BIZ, false),
    LOCATION_NOT_VERIFIED(42507, "工单位置未核验锁定，禁止进入资金或方案链路", 422, ErrorType.BIZ, false),
    HANDOVER_LOCKED(42508, "换届交接锁定中，大额维修动作已熔断", 423, ErrorType.BIZ, false),
    STORAGE_UNAVAILABLE(42509, "附件存储服务暂时不可用", 503, ErrorType.THIRD_PARTY, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    RepairWorkOrderErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.needRetry = needRetry;
    }

    @Override public int getCode() { return code; }
    @Override public String getMessage() { return message; }
    @Override public int getHttpStatus() { return httpStatus; }
    @Override public String getErrorType() { return errorType.name(); }
    @Override public ErrorType getType() { return errorType; }
    @Override public boolean isNeedRetry() { return needRetry; }
}
