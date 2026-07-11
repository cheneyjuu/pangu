package com.pangu.interfaces.web.exception;

public enum SupplierActivationErrorCode implements ErrorCode {

    PARAM_INVALID(42521, "供应商激活参数非法", 400, ErrorType.BIZ, false),
    FORBIDDEN(42522, "无权发送供应商账号激活邀请", 403, ErrorType.BIZ, false),
    INVITATION_NOT_FOUND(42523, "供应商激活邀请不存在", 404, ErrorType.BIZ, false),
    INVITATION_UNAVAILABLE(42524, "供应商激活邀请不可用", 409, ErrorType.BIZ, false),
    INVITATION_EXPIRED(42525, "供应商激活邀请已过期", 410, ErrorType.BIZ, false),
    SMS_CODE_INVALID(42526, "短信验证码错误或已失效", 400, ErrorType.BIZ, false),
    ACCOUNT_DISABLED(42527, "供应商账号已停用", 403, ErrorType.BIZ, false),
    IDENTITY_CONFLICT(42528, "供应商工作身份冲突", 409, ErrorType.BIZ, false),
    SYSTEM_CONFIG_ERROR(42529, "短信验证码服务不可用", 500, ErrorType.SYSTEM, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    SupplierActivationErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
