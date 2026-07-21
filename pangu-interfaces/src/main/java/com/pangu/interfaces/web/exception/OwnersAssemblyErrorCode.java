// 关联业务：定义业主大会会前材料、公示、投票和结算过程的稳定错误码。
package com.pangu.interfaces.web.exception;

public enum OwnersAssemblyErrorCode implements ErrorCode {

    PARAM_INVALID(42601, "业主大会表决参数非法", 400, ErrorType.BIZ, false),
    FORBIDDEN(42602, "当前身份无权操作业主大会表决", 403, ErrorType.BIZ, false),
    NOT_FOUND(42603, "业主大会表决包或事项不存在", 404, ErrorType.BIZ, false),
    INVALID_STATUS(42604, "业主大会表决状态不允许该动作", 409, ErrorType.BIZ, false),
    NOTICE_NOT_COMPLETED(42605, "业主大会公示期或投票开始时间未满足", 409, ErrorType.BIZ, false),
    DELIVERY_REQUIRED(42606, "投票前必须完成送达留痕", 409, ErrorType.BIZ, false),
    AUTH_LEVEL_INSUFFICIENT(42607, "在线表决前请先完成 L2 实名认证", 403, ErrorType.BIZ, false),
    OPID_NOT_OWNED(42608, "该房产身份不属于当前用户", 403, ErrorType.BIZ, false),
    OPID_OUT_OF_SCOPE(42609, "该房产不在本表决范围内或不具备投票资格", 403, ErrorType.BIZ, false),
    VOTE_ALREADY_CAST(42610, "该房产已有有效票，不能重复投票", 409, ErrorType.BIZ, false),
    CONCURRENT_MODIFICATION(42611, "业主大会表决状态已被并发修改，请刷新后重试", 409, ErrorType.SYSTEM, true),
    STORAGE_UNAVAILABLE(42612, "业主大会原始材料存储暂不可用，请稍后重试", 503, ErrorType.SYSTEM, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    OwnersAssemblyErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
