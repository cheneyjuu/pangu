// 关联业务：保存业主本人确认已阅读锁定表决材料并形成线上送达的事实。
package com.pangu.domain.model.voting;

import java.time.Instant;

public record OnlineVotingAcknowledgement(
        Long acknowledgementId,
        Long packageId,
        Long electorateItemId,
        Long tenantId,
        Long accountId,
        Long uid,
        Long opid,
        String packageHash,
        String acknowledgementHash,
        Long unifiedDeliveryId,
        Instant acknowledgedAt
) {
}
