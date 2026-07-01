package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VotingScope;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 投票分母只读读取端口（领域定义；实现在 infrastructure）。
 *
 * <p>区别于写侧 {@code VotingDenominatorResolver}（会落定快照、强校验 Merkle hash）：
 * 本端口是<strong>只读投影</strong>，供「投票进行中」的实时进度看板预览分母总量，
 * <strong>不落快照</strong>。两者复用同一条 room_id 双重去重查询
 * （{@code VotingDenominatorSnapshotMapper.selectDenominatorItems}），故与结算口径零偏差。
 */
public interface VotingDenominatorReader {

    /**
     * 预览分母总量（不落快照）。
     *
     * @param tenantId         租户 ID
     * @param scope            分母范围（UNIT 不支持，实现需拒绝）
     * @param scopeReferenceId BUILDING 时为 building_id；COMMUNITY 可为 null
     * @return 去重后的分母总量；范围内无房产数据时返回零总量（{@code totalArea=0, totalOwnerCount=0}）
     */
    DenominatorTotals previewTotals(Long tenantId, VotingScope scope, Long scopeReferenceId);

    Optional<FrozenDenominatorSnapshot> findFrozenSnapshot(Long subjectId);

    /**
     * 分母总量值对象（只读预览，非法定快照）。
     *
     * @param totalArea       去重后总专有面积
     * @param totalOwnerCount 去重后总业主人数
     */
    record DenominatorTotals(
            BigDecimal totalArea,
            long totalOwnerCount,
            Long denominatorSnapshotId,
            String denominatorMerkleRoot
    ) {
        public DenominatorTotals(BigDecimal totalArea, long totalOwnerCount) {
            this(totalArea, totalOwnerCount, null, null);
        }
    }

    /**
     * 已落定的分母快照元数据。
     */
    record FrozenDenominatorSnapshot(
            Long snapshotId,
            BigDecimal totalArea,
            long totalOwnerCount,
            String merkleRoot
    ) {
    }
}
