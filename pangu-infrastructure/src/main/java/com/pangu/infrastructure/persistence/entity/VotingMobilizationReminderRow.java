package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class VotingMobilizationReminderRow {

    private Long reminderId;
    private Long subjectId;
    private Long tenantId;
    private Long buildingId;
    private Long sentByUserId;
    private Long permissionId;
    private String targetScope;
    private Integer targetCount;
    private String messageTemplate;
    private String message;
    private Long outboxEventId;
    private Instant sentAt;
}
