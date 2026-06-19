package com.pangu.interfaces.web.controller.dto.disclosure;

import com.pangu.domain.model.disclosure.DisclosureStatus;
import com.pangu.domain.model.disclosure.DisclosureType;
import com.pangu.domain.model.disclosure.FinanceDisclosureSnapshot;

import java.time.Instant;

/**
 * 财务公示快照响应体（compose / publish / GET 共用）。
 *
 * <p>暴露稳定的业务字段 + 双签审计字段；不暴露 createTime / updateTime
 * （DB 维度时间戳，业务以 composedAt / lockedAt / publishedAt 为准）。
 */
public record SnapshotResponse(
        Long snapshotId,
        Long tenantId,
        String period,
        DisclosureType disclosureType,
        DisclosureStatus status,
        String dataPayload,
        int statisticsVersion,
        String payloadHash,
        Long composedByUserId,
        Instant composedAt,
        Long governanceLockId,
        Instant lockedAt,
        Instant publishedAt,
        long version
) {
    public static SnapshotResponse from(FinanceDisclosureSnapshot s) {
        return new SnapshotResponse(
                s.getSnapshotId(),
                s.getTenantId(),
                s.getPeriod(),
                s.getDisclosureType(),
                s.getStatus(),
                s.getDataPayload(),
                s.getStatisticsVersion(),
                s.getPayloadHash(),
                s.getComposedByUserId(),
                s.getComposedAt(),
                s.getGovernanceLockId(),
                s.getLockedAt(),
                s.getPublishedAt(),
                s.getVersion()
        );
    }
}
