// 关联业务：向管理端返回业主大会纸质表决材料的逐户送达回执。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.VotingDeliveryRecord;

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
    public static OwnersAssemblyDeliveryResponse from(VotingDeliveryRecord record) {
        return new OwnersAssemblyDeliveryResponse(
                record.deliveryId(),
                record.packageId(),
                record.opid(),
                record.uid(),
                record.deliveryChannel().name(),
                record.deliveryMethod(),
                record.evidenceHash(),
                record.deliveredAt());
    }
}
