package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

/** 报修工单审计事件。 */
public record RepairWorkOrderEvent(
        Long eventId,
        Long workOrderId,
        Long tenantId,
        String action,
        RepairWorkOrderStatus fromStatus,
        RepairWorkOrderStatus toStatus,
        Long actorAccountId,
        String actorIdentityType,
        Long actorIdentityId,
        String remark,
        String payloadJson,
        LocalDateTime createTime
) {
}
