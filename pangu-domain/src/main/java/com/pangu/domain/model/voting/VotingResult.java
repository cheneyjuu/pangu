// 关联业务：承载业主共同决定结算后的双维度参与、选项汇总和通过结论。
package com.pangu.domain.model.voting;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 投票结算结果基类 (泛型高层抽象)
 * @param <S> 具体的投票议题类型
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class VotingResult<S extends VotingSubject> {

    /** 关联的投票议题 */
    private S subject;

    /** 小区计票总面积 */
    private BigDecimal totalArea;

    /** 小区总业主数 */
    private Long totalOwnerCount;

    /** 参会（参与投票）的房产面积 */
    private BigDecimal participatingArea;

    /** 参会（参与投票）的业主人数 */
    private Long participatingOwnerCount;

    /** 是否达到双参与（2/3）法定开会门槛 */
    private boolean quorumSatisfied;

    /** 表决是否通过 */
    private boolean passed;

    /** 赞成票对应的总专有面积 */
    private BigDecimal supportArea;

    /** 赞成票对应的总人数 */
    private Long supportOwnerCount;

    /** 反对票对应的总专有面积 */
    private BigDecimal againstArea;

    /** 反对票对应的总人数 */
    private Long againstOwnerCount;

    /** 弃权票对应的总专有面积 */
    private BigDecimal abstainArea;

    /** 弃权票对应的总人数 */
    private Long abstainOwnerCount;

    /** 实际有效票汇总，不包含系统依据规则形成的认定票。 */
    private VoteTallyBreakdown actualTally;

    /** 未反馈认定票汇总；未产生认定票时为全零。 */
    private VoteTallyBreakdown deemedTally;

    /** 本次结算使用的未反馈认定方式；非正式规则结算时为空。 */
    private VotingNonResponsePolicy nonResponsePolicy;

    /** 有效送达且截止无有效票的唯一表决代表数量。 */
    private Long nonResponseEligibleOwnerCount;

    /** 有效送达且截止无有效票的专有部分面积。 */
    private BigDecimal nonResponseEligibleArea;

    /** “按多数意见认定”时由实际票确定的唯一多数选项。 */
    private VoteChoice majorityChoice;

    /** 逐条未反馈认定记录的稳定聚合摘要。 */
    private String nonResponseDerivationHash;

    /**
     * 获取参与投票的面积占比
     */
    public BigDecimal getParticipatingAreaRatio() {
        if (totalArea == null || totalArea.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return participatingArea.divide(totalArea, 4, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 获取参与投票的人数占比
     */
    public BigDecimal getParticipatingOwnerRatio() {
        if (totalOwnerCount == null || totalOwnerCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(participatingOwnerCount)
                .divide(BigDecimal.valueOf(totalOwnerCount), 4, BigDecimal.ROUND_HALF_UP);
    }
}
