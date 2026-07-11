package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyDeliveryRecord;

import java.time.Instant;

public record OwnersAssemblyDeliveryResponse(
        Long deliveryId,
        Long packageId,
        Long opid,
        Long uid,
        String deliveryChannel,
        String deliveryMethod,
        String evidenceHash,
        Instant deliveredAt
) {
    public static OwnersAssemblyDeliveryResponse from(OwnersAssemblyDeliveryRecord record) {
        return new OwnersAssemblyDeliveryResponse(
                record.deliveryId(),
                record.packageId(),
                record.opid(),
                record.uid(),
                record.deliveryChannel(),
                record.deliveryMethod(),
                record.evidenceHash(),
                record.deliveredAt());
    }
}
