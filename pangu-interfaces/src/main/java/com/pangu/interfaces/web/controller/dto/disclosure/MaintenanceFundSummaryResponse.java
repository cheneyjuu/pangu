// 关联业务：将最近一期已公示的专项维修资金收支摘要安全返回给当前小区业主首页。
package com.pangu.interfaces.web.controller.dto.disclosure;

import com.pangu.application.disclosure.PublishedMaintenanceFundSummary;

import java.math.BigDecimal;
import java.time.Instant;

/** 业主首页的专项维修资金已公示摘要响应。 */
public record MaintenanceFundSummaryResponse(
        Long snapshotId,
        String period,
        BigDecimal inflowAmount,
        BigDecimal outflowAmount,
        Instant publishedAt) {

    public static MaintenanceFundSummaryResponse from(PublishedMaintenanceFundSummary summary) {
        return new MaintenanceFundSummaryResponse(
                summary.snapshotId(),
                summary.period(),
                summary.inflowAmount(),
                summary.outflowAmount(),
                summary.publishedAt());
    }
}
