package com.pangu.application.lock;

/**
 * 治理锁应用层业务异常（含可机读的失败原因）。
 *
 * <p>{@link Reason} 与 web 层 {@code LockErrorCode} 一一映射，由 {@code GlobalExceptionHandler}
 * 完成转换。本异常本身不依赖 web 层，避免反向依赖。
 */
public class GovernanceLockApplicationException extends RuntimeException {

    public enum Reason {
        /** 锁不存在（按 lockId 查询为空）。 */
        LOCK_NOT_FOUND,
        /** 同 (tenant, entityType, entityId) 已经存在锁记录（唯一索引 / Redis 锁兜底）。 */
        LOCK_ALREADY_EXISTS,
        /** 乐观锁失败（并发写）。 */
        LOCK_CONCURRENT_MODIFICATION,
        /** 状态机非法流转（聚合根 transitionTo 抛 IllegalStateException 转化）。 */
        LOCK_INVALID_TRANSITION,
        /** 终签与初签审批人为同一人。 */
        LOCK_SIGNER_CONFLICT,
        /** verifyLocked 时锁不存在或已被双签解锁。 */
        LOCK_NOT_HELD
    }

    private final Reason reason;

    public GovernanceLockApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GovernanceLockApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
