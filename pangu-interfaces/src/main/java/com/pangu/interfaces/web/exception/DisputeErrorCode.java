package com.pangu.interfaces.web.exception;

/**
 * 业主异议业务错误码（feature-scoped 枚举）。
 *
 * <p>错误码段位约定：
 * <pre>
 *   401xx  请求参数级（VALIDATION）—— 状态机非法流转、type/level 不匹配
 *   411xx  业务冲突（BIZ / 409 / 404）—— 不存在 / 越权 / 升级前置条件未满足
 *   411xx  并发冲突（SYSTEM, needRetry=true）—— 乐观锁失败
 * </pre>
 */
public enum DisputeErrorCode implements ErrorCode {

    // ============ 状态机 / 类型校验（VALIDATION / 400） ============
    DISPUTE_INVALID_TRANSITION(40120, "异议状态流转不合法", 400, ErrorType.BIZ, false),
    DISPUTE_TYPE_LEVEL_MISMATCH(40121, "异议类型与当前层级不匹配", 400, ErrorType.BIZ, false),
    DISPUTE_LEVEL_EXCEEDED(40122, "异议已到 Level 4 终态，必须走行政诉讼", 400, ErrorType.BIZ, false),
    DISPUTE_ESCALATE_REQUIRES_REJECTED(40123, "仅 DECIDED_REJECTED 状态可升级到下一级", 400, ErrorType.BIZ, false),

    // ============ 业务冲突（BIZ / 409 / 404 / 403） ============
    DISPUTE_NOT_FOUND(41101, "异议不存在", 404, ErrorType.BIZ, false),
    DISPUTE_NOT_OWNER(41102, "当前用户非该异议发起人", 403, ErrorType.BIZ, false),
    DECISION_DUPLICATE(41103, "该层级决议已存在", 409, ErrorType.BIZ, false),
    DISPUTE_ALREADY_CLOSED(41104, "异议已结案，无法继续操作", 409, ErrorType.BIZ, false),
    EVIDENCE_DISPUTE_CLOSED(41105, "异议已结案，无法补充证据", 409, ErrorType.BIZ, false),

    // ============ 并发（SYSTEM / 409, needRetry） ============
    DISPUTE_CONCURRENT_MODIFICATION(41106, "异议已被并发修改，请刷新后重试", 409, ErrorType.SYSTEM, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    DisputeErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
