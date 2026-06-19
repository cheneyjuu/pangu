package com.pangu.domain.model.dispute;

/**
 * 业主异议类型。M3-1 起始集 = CONTEXT.md 4 类（与 V2.8 chk_dispute_kind 严格对齐）。
 *
 * <p>每种类型决定起始审查层级 {@link #initialLevel()}：
 * <ul>
 *   <li>{@link #EXPENSE_VOUCHER_DISPUTE} —— 业委会一审（Level 1），与 V2.8 chk_dispute_kind_level1 对齐；</li>
 *   <li>其余 3 种 —— 直接进入街道办二审（Level 2），跳过业委会自审（业委会本身可能即被异议方）。</li>
 * </ul>
 */
public enum DisputeKind {

    /** 物业费 / 维修资金支出凭证异议（业主对业委会核销决定不服）。 */
    EXPENSE_VOUCHER_DISPUTE(1),

    /** 工程验收 / 保修期质量异议（业主对业委会验收意见不服）。 */
    PROPOSAL_QUALITY_DISPUTE(2),

    /** 线下票真实性异议（业主对线下投票汇总结果不服）。 */
    OFFLINE_VOTE_DISPUTE(2),

    /** 行政机关驳回不服异议（如街道办作出 REJECTED 后业主不服，但需走专门通道而非直接 escalate）。 */
    ADMINISTRATIVE_REJECTION_DISPUTE(2);

    private final int initialLevel;

    DisputeKind(int initialLevel) {
        this.initialLevel = initialLevel;
    }

    public int initialLevel() {
        return initialLevel;
    }
}
