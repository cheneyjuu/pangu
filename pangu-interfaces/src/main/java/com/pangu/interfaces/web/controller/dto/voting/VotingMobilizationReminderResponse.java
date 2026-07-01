package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.VotingMobilizationReminder;

import java.time.Instant;

public record VotingMobilizationReminderResponse(
        Long reminderId,
        Long subjectId,
        Long tenantId,
        Long buildingId,
        Long sentByUserId,
        Long permissionId,
        String targetScope,
        Integer targetCount,
        String messageTemplate,
        String message,
        Long outboxEventId,
        Instant sentAt
) {
    public static VotingMobilizationReminderResponse from(VotingMobilizationReminder reminder) {
        return new VotingMobilizationReminderResponse(
                reminder.getReminderId(),
                reminder.getSubjectId(),
                reminder.getTenantId(),
                reminder.getBuildingId(),
                reminder.getSentByUserId(),
                reminder.getPermissionId(),
                reminder.getTargetScope(),
                reminder.getTargetCount(),
                reminder.getMessageTemplate(),
                reminder.getMessage(),
                reminder.getOutboxEventId(),
                reminder.getSentAt());
    }
}
