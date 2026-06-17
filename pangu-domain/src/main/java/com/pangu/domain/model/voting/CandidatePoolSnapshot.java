package com.pangu.domain.model.voting;

/**
 * 候选人池实时快照（不可变值对象）。
 *
 * <p>Waiver application 在 {@code 提交申请} 与 {@code 断路器对账} 两个时机各取一份；
 * application 用申请快照写入 waiver 行，断路器用对账快照与申请快照做差异比较。
 *
 * @param partyCount   党员候选人数（qualification_status = APPROVED）
 * @param eligibleCount 合格候选人总数（qualification_status = APPROVED）
 */
public record CandidatePoolSnapshot(long partyCount, long eligibleCount) {

    public CandidatePoolSnapshot {
        if (partyCount < 0 || eligibleCount < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        if (partyCount > eligibleCount) {
            throw new IllegalArgumentException("partyCount must not exceed eligibleCount");
        }
    }
}
