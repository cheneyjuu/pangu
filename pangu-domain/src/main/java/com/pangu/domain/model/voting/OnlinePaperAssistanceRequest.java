// 关联业务：保存互联网表决中业主为本人专有部分申请纸质办理的状态。
package com.pangu.domain.model.voting;

import java.time.Instant;

public record OnlinePaperAssistanceRequest(
        Long requestId,
        Long packageId,
        Long electorateItemId,
        Long tenantId,
        Long accountId,
        Long uid,
        Long opid,
        Status status,
        Instant requestedAt,
        Instant fulfilledAt,
        Instant withdrawnAt,
        Long paperDeliveryId
) {
    public enum Status {
        REQUESTED,
        FULFILLED,
        WITHDRAWN
    }
}
