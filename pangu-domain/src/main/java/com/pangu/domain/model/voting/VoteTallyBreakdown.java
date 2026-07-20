// 关联业务：分别固化实际票与未反馈认定票的人数、面积和各选项汇总。
package com.pangu.domain.model.voting;

import java.math.BigDecimal;

/** 单一票源的双维度汇总。 */
public record VoteTallyBreakdown(
        BigDecimal participatingArea,
        long participatingOwnerCount,
        BigDecimal supportArea,
        long supportOwnerCount,
        BigDecimal againstArea,
        long againstOwnerCount,
        BigDecimal abstainArea,
        long abstainOwnerCount
) {

    public static VoteTallyBreakdown empty() {
        return new VoteTallyBreakdown(
                BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0);
    }
}
