package com.pangu.domain.model.notification;

import java.time.Instant;

public record VotingReminderDeliveryCommand(
        Long outboxEventId,
        Long subjectId,
        Long tenantId,
        Long buildingId,
        Long sentByUserId,
        Long permissionId,
        String targetScope,
        Integer targetCount,
        String messageTemplate,
        String message,
        Instant sentAt
) {
}
