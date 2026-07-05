package com.pangu.interfaces.web.exception;

public enum CommunitySettingsErrorCode implements ErrorCode {

    PARAM_INVALID(42601, "参数非法", 400, ErrorType.BIZ, false),
    FORBIDDEN(42602, "当前身份无权操作社区设置", 403, ErrorType.BIZ, false),
    COMMUNITY_NOT_FOUND(42603, "社区设置不存在", 404, ErrorType.BIZ, false),
    POLICY_NOT_FOUND(42604, "治理规则模板不存在", 404, ErrorType.BIZ, false),
    REVIEW_NOT_FOUND(42605, "计票基数复核申请不存在", 404, ErrorType.BIZ, false),
    REVIEW_INVALID_STATUS(42606, "计票基数复核申请状态不可操作", 409, ErrorType.BIZ, false);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    CommunitySettingsErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
