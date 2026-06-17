package com.pangu.domain.model.voting;

import java.math.BigDecimal;

/**
 * 投票分母不可变值对象。
 *
 * <p>设计原则：
 * <ul>
 *   <li>分母必须由 {@link VotingDenominatorResolver} 一次性双重去重计算后构造，
 *       禁止业务层再绕过 resolver 直接 new。</li>
 *   <li>构造时强校验：面积/人数必须为正，且 snapshotHash 必须为 64-hex SHA256 摘要。
 *       这是「应过未过」群体诉讼的最后一道防线。</li>
 *   <li>引擎只读取该 record，不再接受裸 {@code totalArea/totalOwnerCount}，
 *       从根上消除分母被 SQL 误算 N 倍后偷偷流入投票引擎的可能。</li>
 * </ul>
 *
 * @param totalArea       小区/楼栋/单元 计票总面积（room_id 去重后）
 * @param totalOwnerCount 业主总人数（primary_owner_uid 去重后）
 * @param snapshotHash    分母快照行级 row_hash 的 Merkle root（64 hex）
 * @param snapshotId      关联 t_voting_denominator_snapshot.snapshot_id
 */
public record Denominator(
        BigDecimal totalArea,
        long totalOwnerCount,
        String snapshotHash,
        Long snapshotId
) {
    public Denominator {
        if (totalArea == null || totalArea.signum() <= 0) {
            throw new IllegalArgumentException("totalArea must be a positive BigDecimal");
        }
        if (totalOwnerCount <= 0) {
            throw new IllegalArgumentException("totalOwnerCount must be positive");
        }
        if (snapshotHash == null || snapshotHash.length() != 64) {
            throw new IllegalArgumentException("snapshotHash must be 64-hex SHA256 digest");
        }
        if (snapshotId == null) {
            throw new IllegalArgumentException("snapshotId must not be null");
        }
    }
}
