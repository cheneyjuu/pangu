package com.pangu.domain.model.notification;

import java.time.Instant;

public record VotingReminderDeliveryStatus(
        Long deliveryId,
        Long outboxEventId,
        Long subjectId,
        Long tenantId,
        Long buildingId,
        Long opid,
        Long uid,
        String phoneMasked,
        String channel,
        String messageTemplate,
        Integer deliveryStatus,
        Integer attempts,
        Instant createdAt,
        Instant lastAttemptAt,
        Instant submittedAt,
        Instant confirmedAt,
        Instant failedAt,
        String providerMessageId,
        String lastError
) {
}
