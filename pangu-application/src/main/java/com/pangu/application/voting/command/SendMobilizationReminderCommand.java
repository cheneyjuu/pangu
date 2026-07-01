package com.pangu.application.voting.command;

public record SendMobilizationReminderCommand(
        Long subjectId,
        Long buildingId,
        String message
) {
}
