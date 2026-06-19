package com.pangu.domain.repository;

import com.pangu.domain.model.disclosure.FundLedgerSnapshotData;

/**
 * V2.2 维修资金账户 + 流水的只读查询端口。
 *
 * <p>本期 M2-3 不为 V2.2 (t_maintenance_fund_account / t_fund_ledger_entry) 补建领域聚合根，
 * 仅在 infrastructure 层用 SQL JOIN + GROUP BY 直接聚合。这是 application 层 compose
 * 唯一允许触达 V2.2 数据的通路。
 *
 * <p>实现位置：{@code pangu-infrastructure/.../repository/FundLedgerQueryGatewayImpl}。
 */
public interface FundLedgerQueryGateway {

    /**
     * 聚合指定 tenant + period 的维修资金公示数据。
     *
     * @param tenantId 租户主键
     * @param period   期间字符串：YYYY-MM 或 YYYYQ[1-4]，infra 实现按 Asia/Shanghai (UTC+8)
     *                 解析时间窗 [start, end)
     * @return 账户余额 + 流水汇总
     */
    FundLedgerSnapshotData composeMaintenanceFundData(Long tenantId, String period);
}
