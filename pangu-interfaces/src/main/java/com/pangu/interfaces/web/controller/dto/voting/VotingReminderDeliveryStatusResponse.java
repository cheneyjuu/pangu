package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.notification.VotingReminderDeliveryStatus;

import java.time.Instant;

public record VotingReminderDeliveryStatusResponse(
        Long deliveryId,
        Long outboxEventId,
        Long subjectId,
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
    public static VotingReminderDeliveryStatusResponse from(VotingReminderDeliveryStatus status) {
        return new VotingReminderDeliveryStatusResponse(
                status.deliveryId(),
                status.outboxEventId(),
                status.subjectId(),
                status.buildingId(),
                status.opid(),
                status.uid(),
                status.phoneMasked(),
                status.channel(),
                status.messageTemplate(),
                status.deliveryStatus(),
                status.attempts(),
                status.createdAt(),
                status.lastAttemptAt(),
                status.submittedAt(),
                status.confirmedAt(),
                status.failedAt(),
                status.providerMessageId(),
                status.lastError());
    }
}
