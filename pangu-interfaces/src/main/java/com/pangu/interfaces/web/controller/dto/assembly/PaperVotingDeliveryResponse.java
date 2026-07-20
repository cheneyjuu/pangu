// 关联业务：向管理端展示纸质材料送达登记的核对状态及进入统一送达台账的结果。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.PaperVotingDelivery;

import java.time.Instant;

public record PaperVotingDeliveryResponse(
        Long paperDeliveryId,
        Long packageId,
        Long opid,
        Long proxyAuthorizationId,
        String recipientName,
        String deliveryMethod,
        Long evidenceMaterialId,
        String status,
        Long deliveredByUserId,
        Instant deliveredAt,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote,
        Long unifiedDeliveryId
) {
    public static PaperVotingDeliveryResponse from(PaperVotingDelivery delivery) {
        return new PaperVotingDeliveryResponse(
                delivery.paperDeliveryId(),
                delivery.packageId(),
                delivery.opid(),
                delivery.proxyAuthorizationId(),
                delivery.recipientName(),
                delivery.deliveryMethod(),
                delivery.evidenceSourceId(),
                delivery.status().name(),
                delivery.deliveredByUserId(),
                delivery.deliveredAt(),
                delivery.reviewedByUserId(),
                delivery.reviewedAt(),
                delivery.reviewNote(),
                delivery.unifiedDeliveryId());
    }
}
