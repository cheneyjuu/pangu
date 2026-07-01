package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 议题投票进度只读快照（framework-light 纯值对象）。
 *
 * <p>区别于结算产物 {@link VotingResult}：本对象服务于「投票进行中」的实时进度看板，
 * 由 {@link VotingProgressCalculator} 依据与 {@link AbstractVotingEngine#settle} 完全一致的
 * 去重 / 双 2/3 门槛口径计算，但<strong>不产生结算快照、非法定结论</strong>——结算快照才是唯一法定结论。
 * 对已立项冰封分母的议题，本对象会携带分母快照 ID 与 Merkle root，供前端展示存证依据。
 *
 * <p>SETTLED 态进度可由结算快照直接构造（participating/quorum/passed 精确）；
 * 此时 support 字段因快照表无 support 列而为 null，调用方需容忍。
 *
 * @param subjectId               议题 ID
 * @param status                  议题状态
 * @param scope                   分母范围
 * @param scopeReferenceId        范围引用 ID（BUILDING 时为 building_id；可空）
 * @param totalArea               分母总专有面积
 * @param totalOwnerCount         分母总业主人数
 * @param participatingArea       已参与投票的专有面积（按 uid+opid 去重）
 * @param participatingOwnerCount 已参与投票的业主人数（按 uid 去重）
 * @param supportArea             赞成票对应的专有面积（SETTLED 态可空）
 * @param supportOwnerCount       赞成票对应的业主人数（SETTLED 态可空）
 * @param quorumSatisfied         是否达到双 2/3 法定门槛
 * @param settled                 是否已结算（true 表示数据来自法定快照）
 * @param passed                  议题是否通过（仅 settled 时有意义）
 * @param denominatorSnapshotId   分母快照 ID；已冰封存证时非空
 * @param denominatorMerkleRoot   分母行级明细 Merkle root；已冰封存证时非空
 */
public record VotingProgress(
        Long subjectId,
        SubjectStatus status,
        VotingScope scope,
        Long scopeReferenceId,
        BigDecimal totalArea,
        long totalOwnerCount,
        BigDecimal participatingArea,
        long participatingOwnerCount,
        BigDecimal supportArea,
        Long supportOwnerCount,
        boolean quorumSatisfied,
        boolean settled,
        boolean passed,
        Long denominatorSnapshotId,
        String denominatorMerkleRoot
) {

    /** 参与面积占比（4 位小数，分母 0 返 0；口径同 {@link VotingResult}）。 */
    public BigDecimal participatingAreaRatio() {
        return ratio(participatingArea, totalArea);
    }

    /** 参与人数占比（4 位小数，分母 0 返 0）。 */
    public BigDecimal participatingOwnerRatio() {
        return ratio(BigDecimal.valueOf(participatingOwnerCount), BigDecimal.valueOf(totalOwnerCount));
    }

    /** 赞成面积占比（4 位小数；support 为空或分母 0 返 0）。 */
    public BigDecimal supportAreaRatio() {
        return ratio(supportArea, totalArea);
    }

    /** 赞成人数占比（4 位小数；support 为空或分母 0 返 0）。 */
    public BigDecimal supportOwnerRatio() {
        return ratio(supportOwnerCount == null ? null : BigDecimal.valueOf(supportOwnerCount),
                BigDecimal.valueOf(totalOwnerCount));
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }
}
