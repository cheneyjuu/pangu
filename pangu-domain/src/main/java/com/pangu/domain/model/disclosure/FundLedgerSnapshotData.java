package com.pangu.domain.model.disclosure;

import java.math.BigDecimal;
import java.util.List;

/**
 * V2.2 维修资金账户 + 流水的只读聚合载体（不是聚合根）。
 *
 * <p>由 {@code FundLedgerQueryGateway.composeMaintenanceFundData(...)} 在 application 层调用
 * compose 时构造，仅作数据搬运；不持久化、不参与领域行为。
 *
 * @param accounts        当前 tenant 全部账户余额
 * @param entrySummaries  按 (业务类型, 借贷方向) 在期间内的流水汇总
 */
public record FundLedgerSnapshotData(
        List<AccountBalance> accounts,
        List<EntrySummary> entrySummaries) {

    public FundLedgerSnapshotData {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        entrySummaries = entrySummaries == null ? List.of() : List.copyOf(entrySummaries);
    }

    /**
     * 单账户余额行。
     *
     * @param accountId      账户主键
     * @param accountLevel   账户层级 1-COMMUNITY / 2-BUILDING / 3-UNIT / 4-ROOM
     * @param referenceId    层级对应的引用主键（小区 / 楼栋 / 单元 / 房间）
     * @param totalBalance   账户总余额
     * @param frozenBalance  冻结金额
     */
    public record AccountBalance(
            Long accountId,
            Integer accountLevel,
            Long referenceId,
            BigDecimal totalBalance,
            BigDecimal frozenBalance) {
    }

    /**
     * 期间流水汇总。
     *
     * @param businessType 业务类型 1~6（详见 V2.2 表注释）
     * @param direction    1-DEBIT / 2-CREDIT
     * @param entryCount   汇总笔数
     * @param totalAmount  汇总金额
     */
    public record EntrySummary(
            Integer businessType,
            Integer direction,
            Long entryCount,
            BigDecimal totalAmount) {
    }
}
