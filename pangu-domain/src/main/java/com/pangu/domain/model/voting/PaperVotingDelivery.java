// 关联业务：保存纸质表决材料逐户送达的原始登记、核对结论及统一送达记录引用。
package com.pangu.domain.model.voting;

import java.time.Instant;

/** 纸质渠道送达登记；只有 CONFIRMED 状态才对应统一执行内核中的有效送达。 */
public record PaperVotingDelivery(
        Long paperDeliveryId,
        Long packageId,
        Long electorateItemId,
        Long tenantId,
        Long opid,
        String recipientName,
        String deliveryMethod,
        String evidenceSourceType,
        Long evidenceSourceId,
        String evidenceHash,
        Long deliveredByUserId,
        Instant deliveredAt,
        Status status,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote,
        Long unifiedDeliveryId,
        Instant createTime,
        Instant updateTime,
        Long version
) {

    public enum Status {
        PENDING_REVIEW,
        CONFIRMED,
        REJECTED
    }
}
