// 关联业务：保存一次正式表决在锁定时形成的专有部分、表决代表和计票基数快照。
package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 表决人名册快照。
 *
 * <p>每个 {@link Item} 对应一个专有部分；共有产权只保留一个已经确认的表决代表，
 * 其余共有人仍进入 {@code coOwnerUids} 以便审计还原。
 */
public record VotingElectorateSnapshot(
        Long snapshotId,
        Long packageId,
        Long tenantId,
        VotingScope scope,
        Long scopeReferenceId,
        BigDecimal totalArea,
        long totalOwnerCount,
        long itemCount,
        String aggregateHash,
        Instant frozenAt,
        List<Item> items
) {

    public VotingElectorateSnapshot {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public VotingElectorateSnapshot withSnapshotId(Long assignedSnapshotId) {
        return new VotingElectorateSnapshot(
                assignedSnapshotId, packageId, tenantId, scope, scopeReferenceId,
                totalArea, totalOwnerCount, itemCount, aggregateHash, frozenAt, items);
    }

    /** 冻结名册中的一个专有部分及其唯一表决代表。 */
    public record Item(
            Long snapshotItemId,
            Long snapshotId,
            Long rosterId,
            Long roomId,
            Long buildingId,
            BigDecimal certifiedArea,
            Long representativeOpid,
            Long representativeUid,
            List<Long> coOwnerUids,
            String rowHash
    ) {
        public Item {
            coOwnerUids = coOwnerUids == null ? List.of() : List.copyOf(coOwnerUids);
        }
    }

    /** 从房屋名册和已核验产权关系读取的候选行；账户状态不属于表决权判断依据。 */
    public record Candidate(
            Long rosterId,
            Long roomId,
            Long buildingId,
            BigDecimal certifiedArea,
            Long opid,
            Long uid,
            boolean votingDelegate
    ) {
    }
}
