package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class VotingReminderDeliveryRow {

    private Long deliveryId;
    private Long outboxEventId;
    private Long subjectId;
    private Long tenantId;
    private Long buildingId;
    private Long opid;
    private Long uid;
    private String phone;
    private String channel;
    private String messageTemplate;
    private String message;
    private Integer deliveryStatus;
    private Integer attempts;
    private Instant createdAt;
    private Instant lastAttemptAt;
    private Instant submittedAt;
    private Instant confirmedAt;
    private Instant failedAt;
    private String providerMessageId;
    private String lastError;
}
