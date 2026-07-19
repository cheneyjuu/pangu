// 关联业务：记录正式表决材料向冻结名册内表决代表完成送达的事实。
package com.pangu.domain.model.voting;

import java.time.Instant;

/** 通用表决送达记录，渠道与送达方式分开保存。 */
public record VotingDeliveryRecord(
        Long deliveryId,
        Long packageId,
        Long electorateItemId,
        Long tenantId,
        Long opid,
        Long uid,
        VoteChannel deliveryChannel,
        String deliveryMethod,
        String evidenceHash,
        Long deliveredByUserId,
        Instant deliveredAt
) {
}
