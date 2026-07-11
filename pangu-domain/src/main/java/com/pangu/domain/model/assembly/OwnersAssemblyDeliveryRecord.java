package com.pangu.domain.model.assembly;

import java.time.Instant;

/** 业主大会纸质送达或线上推送记录。 */
public record OwnersAssemblyDeliveryRecord(
        Long deliveryId,
        Long packageId,
        Long tenantId,
        Long opid,
        Long uid,
        String deliveryChannel,
        String deliveryMethod,
        String evidenceHash,
        Long deliveredByUserId,
        Instant deliveredAt
) {
}
