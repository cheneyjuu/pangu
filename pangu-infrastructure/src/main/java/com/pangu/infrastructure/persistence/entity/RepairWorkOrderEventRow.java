package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairWorkOrderEventRow {
    private Long eventId;
    private Long workOrderId;
    private Long tenantId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private Long actorAccountId;
    private String actorIdentityType;
    private Long actorIdentityId;
    private String remark;
    private String payloadJson;
    private LocalDateTime createTime;
}
