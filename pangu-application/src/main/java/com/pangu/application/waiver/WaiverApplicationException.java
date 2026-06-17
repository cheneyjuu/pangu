package com.pangu.application.waiver;

import com.pangu.domain.policy.ReasonTextPolicy;

/**
 * Waiver 应用层业务异常（含可机读的失败原因）。
 *
 * <p>{@link Reason} 与 web 层 {@code ElectionErrorCode} 一一映射，由 Phase 6 的
 * {@code GlobalExceptionHandler} 完成转换。本异常本身不依赖 web 层，避免反向依赖。
 */
public class WaiverApplicationException extends RuntimeException {

    public enum Reason {
        /** 当前议题已存在活跃 waiver（DB 唯一索引 / Redis 锁触发）。 */
        WAIVER_ALREADY_PENDING,
        /** 申请人非居委会用户（dept_type != 2）。 */
        INITIATOR_NOT_COMMITTEE,
        /** 申请人租户与议题租户不一致。 */
        TENANT_MISMATCH,
        /** 议题不存在或不允许发起 waiver（已截止/已结算）。 */
        SUBJECT_NOT_ELIGIBLE,
        /** 申请理由未通过水文检测。 */
        REASON_REJECTED,
        /** 申请理由实质字符不足。 */
        REASON_TOO_SHORT,
        /** 状态流转不合法（聚合根 transitionTo 抛 IllegalStateException 转化）。 */
        INVALID_TRANSITION,
        /** 审批人部门类型不符。 */
        APPROVER_DEPT_INVALID,
        /** 终审与初审审批人冲突（同一用户）。 */
        APPROVER_CONFLICT,
        /** 找不到 waiver。 */
        WAIVER_NOT_FOUND,
        /** 乐观锁失败（并发写）。 */
        CONCURRENT_MODIFICATION
    }

    private final Reason reason;
    private final ReasonTextPolicy.FailureReason reasonTextFailure;

    public WaiverApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
        this.reasonTextFailure = null;
    }

    public WaiverApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.reasonTextFailure = null;
    }

    /**
     * 水文检测失败专用构造：保留 {@link ReasonTextPolicy.FailureReason} 细分子原因，
     * 便于 web 层映射到具体的 {@code ElectionErrorCode}。
     */
    public WaiverApplicationException(ReasonTextPolicy.FailureReason failureReason, String message) {
        super(message);
        this.reason = mapFromTextFailure(failureReason);
        this.reasonTextFailure = failureReason;
    }

    public Reason getReason() {
        return reason;
    }

    public ReasonTextPolicy.FailureReason getReasonTextFailure() {
        return reasonTextFailure;
    }

    private static Reason mapFromTextFailure(ReasonTextPolicy.FailureReason f) {
        if (f == ReasonTextPolicy.FailureReason.TOO_SHORT) {
            return Reason.REASON_TOO_SHORT;
        }
        return Reason.REASON_REJECTED;
    }
}
