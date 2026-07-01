package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 司法链 outbox 行（与 t_outbox_event 一一对应）。
 */
@Data
public class OutboxEventRow {
    private Long eventId;
    private Integer eventType;
    private Long businessRefId;
    private Long tenantId;
    private String payloadJson;
    private Integer status;
    private Integer attempts;
    private String lastError;
    private Instant lastAttemptAt;
    private Instant confirmedAt;
}
