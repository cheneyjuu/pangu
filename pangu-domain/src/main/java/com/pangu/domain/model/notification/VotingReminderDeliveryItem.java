package com.pangu.domain.model.notification;

public record VotingReminderDeliveryItem(
        Long deliveryId,
        Long outboxEventId,
        Long subjectId,
        Long tenantId,
        Long buildingId,
        Long opid,
        Long uid,
        String phone,
        String channel,
        String messageTemplate,
        String message,
        Integer attempts
) {
}
