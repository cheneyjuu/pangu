package com.pangu.interfaces.web.exception;

/**
 * 通用治理锁业务错误码（feature-scoped 枚举，对齐 {@link CommonErrorCode} / {@link ElectionErrorCode} 体系）。
 *
 * <p>错误码段位约定：
 * <pre>
 *   401xx  请求参数级（VALIDATION）—— 状态机非法流转、签名人冲突
 *   410xx  业务冲突（BUSINESS / 409 / 404）—— 锁已存在 / 不存在 / 已解锁
 *   410xx  并发冲突（SYSTEM, needRetry=true）—— 乐观锁失败
 * </pre>
 *
 * <p>{@code errorType} 复用 {@link ErrorType}（BIZ / SYSTEM / THIRD_PARTY）；
 * 与 {@code ElectionErrorCode} 共享 {@link CommonErrorCode#SYSTEM_ERROR} 兜底。
 */
public enum LockErrorCode implements ErrorCode {

    // ============ 状态机 / 签名（VALIDATION / 400） ============
    LOCK_INVALID_TRANSITION(40110, "治理锁状态流转不合法", 400, ErrorType.BIZ, false),
    LOCK_SIGNER_CONFLICT(40111, "终签与初签审批人不能为同一人", 400, ErrorType.BIZ, false),

    // ============ 业务冲突（BUSINESS / 409 / 404） ============
    LOCK_ALREADY_EXISTS(41001, "目标实体已存在锁定记录", 409, ErrorType.BIZ, false),
    LOCK_NOT_HELD(41002, "目标实体未被锁定或已被双签解锁", 409, ErrorType.BIZ, false),
    LOCK_NOT_FOUND(41003, "治理锁不存在", 404, ErrorType.BIZ, false),

    // ============ 并发（SYSTEM / 409, needRetry） ============
    LOCK_CONCURRENT_MODIFICATION(41004, "治理锁已被并发修改，请刷新后重试", 409, ErrorType.SYSTEM, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    LockErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
