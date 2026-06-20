package com.pangu.interfaces.web.exception;

/**
 * 财务公示业务错误码（M2-3，对齐 {@link LockErrorCode} / {@link ElectionErrorCode} 体系）。
 *
 * <p>错误码段位约定：411xx —— 财务公示快照与差分审计相关业务冲突。
 * <ul>
 *   <li>{@code DISCLOSURE_TYPE_NOT_SUPPORTED}：本期仅 MAINTENANCE_FUND 可用，COMMON_FUND 仅占位；</li>
 *   <li>{@code DISCLOSURE_NOT_PUBLISHED}：业主侧 GET 时快照状态低于 PUBLISHED；</li>
 *   <li>{@code SNAPSHOT_DUPLICATE}：唯一索引兜底（同 tenant+type+period+statisticsVersion 已存在）；</li>
 *   <li>{@code SNAPSHOT_NOT_FOUND} / {@code SNAPSHOT_INVALID_TRANSITION}：状态机层异常；</li>
 *   <li>{@code COMPARE_INVALID_PAIR}：差分快照对不合法（同 id / 跨租户 / 类型不一致 / 时间逆序）；</li>
 *   <li>{@code LEDGER_QUERY_EMPTY}：compose 期间 V2.2 流水/账户为空；</li>
 *   <li>{@code SNAPSHOT_CONCURRENT_MODIFICATION}：乐观锁冲突，{@code needRetry=true}。</li>
 *   <li>{@code HANDOVER_IN_PROGRESS}：换届选举在途，财务公示发布被熔断（HANDOVER_LOCK）。</li>
 * </ul>
 */
public enum DisclosureErrorCode implements ErrorCode {

    DISCLOSURE_TYPE_NOT_SUPPORTED(41101, "财务公示类型本期不被支持", 409, ErrorType.BIZ, false),
    DISCLOSURE_NOT_PUBLISHED(41102, "财务公示快照尚未发布，禁止读取", 409, ErrorType.BIZ, false),
    SNAPSHOT_DUPLICATE(41103, "财务公示快照已存在", 409, ErrorType.BIZ, false),
    SNAPSHOT_NOT_FOUND(41104, "财务公示快照不存在", 404, ErrorType.BIZ, false),
    SNAPSHOT_INVALID_TRANSITION(41105, "财务公示快照状态流转不合法", 409, ErrorType.BIZ, false),
    COMPARE_INVALID_PAIR(41106, "差分快照对不合法", 400, ErrorType.BIZ, false),
    LEDGER_QUERY_EMPTY(41107, "目标期间内无任何账户与流水可聚合", 409, ErrorType.BIZ, false),
    SNAPSHOT_CONCURRENT_MODIFICATION(41108, "财务公示快照已被并发修改，请刷新后重试", 409, ErrorType.SYSTEM, true),
    HANDOVER_IN_PROGRESS(41109, "换届选举进行中，财务公示发布已熔断", 409, ErrorType.BIZ, false);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    DisclosureErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
