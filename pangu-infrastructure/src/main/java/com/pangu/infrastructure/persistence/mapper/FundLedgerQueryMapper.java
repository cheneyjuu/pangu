package com.pangu.infrastructure.persistence.mapper;

import com.pangu.domain.model.disclosure.FundLedgerSnapshotData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * V2.2 维修资金账户 + 流水的只读聚合 Mapper。
 *
 * <p>本期 M2-3 不为 V2.2 表补建领域聚合根；本 mapper 仅提供两条聚合查询，
 * 供 {@code FundLedgerQueryGatewayImpl} 拼装 {@link FundLedgerSnapshotData}：
 * <ul>
 *   <li>{@link #selectAccountBalances} —— 当前 tenant 全部账户余额；</li>
 *   <li>{@link #selectEntrySummariesInWindow} —— 按 (业务类型, 借贷方向) 在
 *       {@code [startTs, endTs)} 内的流水汇总。</li>
 * </ul>
 */
@Mapper
public interface FundLedgerQueryMapper {

    List<FundLedgerSnapshotData.AccountBalance> selectAccountBalances(@Param("tenantId") Long tenantId);

    List<FundLedgerSnapshotData.EntrySummary> selectEntrySummariesInWindow(
            @Param("tenantId") Long tenantId,
            @Param("startTs") Instant startTs,
            @Param("endTs") Instant endTs);
}
