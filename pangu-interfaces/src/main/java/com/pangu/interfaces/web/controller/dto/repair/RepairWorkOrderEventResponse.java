package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairWorkOrderEvent;

import java.time.LocalDateTime;

public record RepairWorkOrderEventResponse(
        Long eventId,
        Long workOrderId,
        Long tenantId,
        String action,
        String fromStatus,
        String toStatus,
        Long actorAccountId,
        String actorIdentityType,
        Long actorIdentityId,
        String remark,
        String payloadJson,
        LocalDateTime createTime
) {
    public static RepairWorkOrderEventResponse from(RepairWorkOrderEvent e) {
        return new RepairWorkOrderEventResponse(
                e.eventId(),
                e.workOrderId(),
                e.tenantId(),
                e.action(),
                e.fromStatus() == null ? null : e.fromStatus().name(),
                e.toStatus() == null ? null : e.toStatus().name(),
                e.actorAccountId(),
                e.actorIdentityType(),
                e.actorIdentityId(),
                e.remark(),
                e.payloadJson(),
                e.createTime());
    }
}
