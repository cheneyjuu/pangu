package com.pangu.domain.model.waiver;

import com.pangu.domain.model.voting.RatioCheckTrigger;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 断路器三点对账记录（不可变值对象）。
 *
 * <p>对应 {@code t_waiver_snapshot_comparison}：每次对账无论是否触发撤销
 * 都强制写入一条审计快照，提供「事中可解释、事后可还原」的合规凭证。
 *
 * @param waiverId             关联的放宽申请
 * @param subjectId            议题 ID
 * @param trigger              触发时机
 * @param recordedPartyCount   申请时党员池快照
 * @param recordedEligibleCount 申请时合格候选人池快照
 * @param recordedRatio        申请时党员比例
 * @param currentPartyCount    对账时党员池
 * @param currentEligibleCount 对账时合格候选人池
 * @param currentNaturalRatio  对账时党员比例
 * @param actionTaken          采取的动作
 * @param auditHash            行级审计 hash（防篡改）
 * @param snapshotAt           对账时间戳
 */
public record SnapshotComparison(
        Long waiverId,
        Long subjectId,
        RatioCheckTrigger trigger,
        long recordedPartyCount,
        long recordedEligibleCount,
        BigDecimal recordedRatio,
        long currentPartyCount,
        long currentEligibleCount,
        BigDecimal currentNaturalRatio,
        SnapshotAction actionTaken,
        String auditHash,
        Instant snapshotAt
) {

    public long deltaParty() {
        return currentPartyCount - recordedPartyCount;
    }

    public long deltaEligible() {
        return currentEligibleCount - recordedEligibleCount;
    }

    public enum SnapshotAction {
        /** 自然比例仍未达 50%，放宽继续生效。 */
        NONE(1),
        /** 自然比例已达 50%，自动撤销 waiver。 */
        REVOKED_BY_SYSTEM(2),
        /** 党员池倒退（人数减少），告警但不撤销。 */
        WARN_REGRESSION(3);

        private final int dbValue;

        SnapshotAction(int dbValue) {
            this.dbValue = dbValue;
        }

        public int getDbValue() {
            return dbValue;
        }
    }
}
