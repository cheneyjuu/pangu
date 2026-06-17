package com.pangu.domain.model.voting;

/**
 * 党员比例断路器对账触发时机。
 *
 * <p>Waiver 通过 → 投票截止之间存在时间窗，期间党员池可能因新候选人加入而自然达到 50%。
 * 系统在三个关键时刻强制对账，以避免「应该不放宽却用了放宽 ratio」的合规事故：
 * <ul>
 *   <li>{@link #PUBLISH_DAY}：候选人公示日（最后一次允许人工撤销）</li>
 *   <li>{@link #VOTE_END}：投票截止瞬间（最后一次自动撤销）</li>
 *   <li>{@link #SETTLE}：结算前（兜底再核对一次，防 PUBLISH_DAY/VOTE_END 任何一道遗漏）</li>
 * </ul>
 */
public enum RatioCheckTrigger {

    /**
     * 结算前兜底触发（dbValue=1）。
     * <p>编号顺序与 V2.1 {@code t_waiver_snapshot_comparison.trigger_phase} 注释保持一致。
     */
    SETTLE(1),

    /** 候选人公示日触发（dbValue=2）。 */
    PUBLISH_DAY(2),

    /** 投票截止瞬间触发（dbValue=3）。 */
    VOTE_END(3);

    private final int dbValue;

    RatioCheckTrigger(int dbValue) {
        this.dbValue = dbValue;
    }

    public int getDbValue() {
        return dbValue;
    }
}
