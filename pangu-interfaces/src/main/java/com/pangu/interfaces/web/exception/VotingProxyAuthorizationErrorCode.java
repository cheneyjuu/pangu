// 关联业务：定义书面委托登记、核验、撤销和纸票使用的接口错误语义。
package com.pangu.interfaces.web.exception;

public enum VotingProxyAuthorizationErrorCode implements ErrorCode {
    FORBIDDEN(40370, "当前工作身份无权办理书面委托", 403, ErrorType.BIZ, false),
    NOT_FOUND(40470, "书面委托登记不存在", 404, ErrorType.BIZ, false),
    INVALID_ARGUMENT(40070, "书面委托信息不完整或格式不正确", 400, ErrorType.BIZ, false),
    INVALID_STATUS(40970, "书面委托当前状态不允许该操作", 409, ErrorType.BIZ, false),
    DUPLICATE(40971, "该房屋已有待核对或已确认的书面委托", 409, ErrorType.BIZ, false),
    CONCURRENT_MODIFICATION(40972, "书面委托已被其他工作人员处理", 409, ErrorType.SYSTEM, true),
    STORAGE_UNAVAILABLE(50070, "书面委托原件暂时无法保存或读取", 500, ErrorType.SYSTEM, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    VotingProxyAuthorizationErrorCode(
            int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.needRetry = needRetry;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getErrorType() {
        return errorType.name();
    }

    @Override
    public ErrorType getType() {
        return errorType;
    }

    @Override
    public boolean isNeedRetry() {
        return needRetry;
    }
}
