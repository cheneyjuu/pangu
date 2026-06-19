package com.pangu.domain.model.dispute;

/**
 * 业主异议主表状态 ENUM。21 个常量，与 V2.8 {@code chk_dispute_status} 严格对齐。
 *
 * <p>状态机骨架（详见 {@link Dispute} 聚合根的 ALLOWED_TRANSITIONS）：
 * <pre>
 *   RAISED ──► UNDER_REVIEW_LEVEL_N ──► DECIDED_LEVEL_N_(UPHELD|REJECTED|PARTIAL)
 *                                            │
 *                                  REJECTED ─┴─► UNDER_REVIEW_LEVEL_(N+1) ...
 *                                  UPHELD/PARTIAL ─► CLOSED_FINAL（业主接受）
 *   RAISED / DECIDED_LEVEL_4_REJECTED ──► LITIGATION_FILED（行政诉讼终态，等 M3-4 判决回流）
 *   RAISED / UNDER_REVIEW_* ──► WITHDRAWN
 * </pre>
 */
public enum DisputeStatus {

    RAISED,

    UNDER_REVIEW_LEVEL_1,
    DECIDED_LEVEL_1_UPHELD,
    DECIDED_LEVEL_1_REJECTED,
    DECIDED_LEVEL_1_PARTIAL,

    UNDER_REVIEW_LEVEL_2,
    DECIDED_LEVEL_2_UPHELD,
    DECIDED_LEVEL_2_REJECTED,
    DECIDED_LEVEL_2_PARTIAL,

    UNDER_REVIEW_LEVEL_3,
    DECIDED_LEVEL_3_UPHELD,
    DECIDED_LEVEL_3_REJECTED,
    DECIDED_LEVEL_3_PARTIAL,

    UNDER_REVIEW_LEVEL_4,
    DECIDED_LEVEL_4_UPHELD,
    DECIDED_LEVEL_4_REJECTED,
    DECIDED_LEVEL_4_PARTIAL,

    LITIGATION_FILED,
    CLOSED_FINAL,
    WITHDRAWN;

    /**
     * 该状态对应的审查层级（1-4）；RAISED / LITIGATION_FILED / CLOSED_FINAL / WITHDRAWN 返回 0。
     */
    public int levelOrZero() {
        return switch (this) {
            case UNDER_REVIEW_LEVEL_1, DECIDED_LEVEL_1_UPHELD, DECIDED_LEVEL_1_REJECTED, DECIDED_LEVEL_1_PARTIAL -> 1;
            case UNDER_REVIEW_LEVEL_2, DECIDED_LEVEL_2_UPHELD, DECIDED_LEVEL_2_REJECTED, DECIDED_LEVEL_2_PARTIAL -> 2;
            case UNDER_REVIEW_LEVEL_3, DECIDED_LEVEL_3_UPHELD, DECIDED_LEVEL_3_REJECTED, DECIDED_LEVEL_3_PARTIAL -> 3;
            case UNDER_REVIEW_LEVEL_4, DECIDED_LEVEL_4_UPHELD, DECIDED_LEVEL_4_REJECTED, DECIDED_LEVEL_4_PARTIAL -> 4;
            default -> 0;
        };
    }

    public boolean isUnderReview() {
        return this == UNDER_REVIEW_LEVEL_1 || this == UNDER_REVIEW_LEVEL_2
                || this == UNDER_REVIEW_LEVEL_3 || this == UNDER_REVIEW_LEVEL_4;
    }

    public boolean isDecided() {
        return this == DECIDED_LEVEL_1_UPHELD || this == DECIDED_LEVEL_1_REJECTED || this == DECIDED_LEVEL_1_PARTIAL
                || this == DECIDED_LEVEL_2_UPHELD || this == DECIDED_LEVEL_2_REJECTED || this == DECIDED_LEVEL_2_PARTIAL
                || this == DECIDED_LEVEL_3_UPHELD || this == DECIDED_LEVEL_3_REJECTED || this == DECIDED_LEVEL_3_PARTIAL
                || this == DECIDED_LEVEL_4_UPHELD || this == DECIDED_LEVEL_4_REJECTED || this == DECIDED_LEVEL_4_PARTIAL;
    }

    public boolean isRejected() {
        return this == DECIDED_LEVEL_1_REJECTED || this == DECIDED_LEVEL_2_REJECTED
                || this == DECIDED_LEVEL_3_REJECTED || this == DECIDED_LEVEL_4_REJECTED;
    }

    /** {@link #CLOSED_FINAL} / {@link #WITHDRAWN} 才算终态；{@link #LITIGATION_FILED} 等判决回流。 */
    public boolean isClosed() {
        return this == CLOSED_FINAL || this == WITHDRAWN;
    }

    /**
     * 由 {@link DecisionKind} 推导对应 level 的 DECIDED_LEVEL_N_* 状态。
     */
    public static DisputeStatus decidedFor(int level, DecisionKind kind) {
        return switch (level) {
            case 1 -> switch (kind) {
                case UPHELD -> DECIDED_LEVEL_1_UPHELD;
                case REJECTED -> DECIDED_LEVEL_1_REJECTED;
                case PARTIAL_UPHELD -> DECIDED_LEVEL_1_PARTIAL;
            };
            case 2 -> switch (kind) {
                case UPHELD -> DECIDED_LEVEL_2_UPHELD;
                case REJECTED -> DECIDED_LEVEL_2_REJECTED;
                case PARTIAL_UPHELD -> DECIDED_LEVEL_2_PARTIAL;
            };
            case 3 -> switch (kind) {
                case UPHELD -> DECIDED_LEVEL_3_UPHELD;
                case REJECTED -> DECIDED_LEVEL_3_REJECTED;
                case PARTIAL_UPHELD -> DECIDED_LEVEL_3_PARTIAL;
            };
            case 4 -> switch (kind) {
                case UPHELD -> DECIDED_LEVEL_4_UPHELD;
                case REJECTED -> DECIDED_LEVEL_4_REJECTED;
                case PARTIAL_UPHELD -> DECIDED_LEVEL_4_PARTIAL;
            };
            default -> throw new IllegalArgumentException("Invalid review level: " + level);
        };
    }

    /** 由 level 推导对应 UNDER_REVIEW_LEVEL_N 状态。 */
    public static DisputeStatus underReviewFor(int level) {
        return switch (level) {
            case 1 -> UNDER_REVIEW_LEVEL_1;
            case 2 -> UNDER_REVIEW_LEVEL_2;
            case 3 -> UNDER_REVIEW_LEVEL_3;
            case 4 -> UNDER_REVIEW_LEVEL_4;
            default -> throw new IllegalArgumentException("Invalid review level: " + level);
        };
    }
}
