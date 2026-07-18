// 关联业务：向业主首页提供最近一期已公示的专项维修资金入账、支出汇总，不混入尚未启用的公共收益数据。
package com.pangu.application.disclosure;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 最近一期已公示专项维修资金快照的首页摘要。
 *
 * <p>金额来自已发布快照中的台账方向汇总，而非实时余额或前端推算：
 * {@code inflowAmount} 对应方向 1（入账），{@code outflowAmount} 对应方向 2（出账）。
 */
public record PublishedMaintenanceFundSummary(
        Long snapshotId,
        String period,
        BigDecimal inflowAmount,
        BigDecimal outflowAmount,
        Instant publishedAt) {
}
