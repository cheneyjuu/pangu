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
