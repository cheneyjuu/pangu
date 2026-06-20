package com.pangu.application.disclosure;

/**
 * 财务公示应用层业务异常（含可机读的失败原因）。
 *
 * <p>{@link Reason} 与 web 层 {@code DisclosureErrorCode} 一一映射，
 * 由 {@code GlobalExceptionHandler} 完成转换。本异常本身不依赖 web 层。
 */
public class FinanceDisclosureApplicationException extends RuntimeException {

    public enum Reason {
        /** 快照不存在（按 snapshotId 查询为空）。 */
        SNAPSHOT_NOT_FOUND,
        /** 同 (tenant, type, period, statisticsVersion) 已经存在；或 LOCKED/PUBLISHED 已唯一存在。 */
        SNAPSHOT_DUPLICATE,
        /** 状态机非法流转（聚合根 transitionTo 抛 IllegalStateException 转化）。 */
        SNAPSHOT_INVALID_TRANSITION,
        /** disclosureType 当前不被支持（如 COMMON_FUND 占位但未启用）。 */
        DISCLOSURE_TYPE_NOT_SUPPORTED,
        /** 业主 / 外部读取时 snapshot 状态低于 PUBLISHED。 */
        DISCLOSURE_NOT_PUBLISHED,
        /** compare 指定的 (prev, curr) 不合法（同一条 / 跨租户 / 类型不一致 / 时间逆序）。 */
        COMPARE_INVALID_PAIR,
        /** compose 指定的 period 内没有任何账户与流水可聚合。 */
        LEDGER_QUERY_EMPTY,
        /** 乐观锁失败（并发写）。 */
        SNAPSHOT_CONCURRENT_MODIFICATION,
        /** 换届选举在途，财务公示发布被熔断（HANDOVER_LOCK）。 */
        HANDOVER_IN_PROGRESS
    }

    private final Reason reason;

    public FinanceDisclosureApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FinanceDisclosureApplicationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
