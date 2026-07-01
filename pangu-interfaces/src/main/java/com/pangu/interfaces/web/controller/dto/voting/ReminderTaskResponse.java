package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.ReminderTask;
import com.pangu.domain.model.voting.SubjectType;

import java.time.Instant;

public record ReminderTaskResponse(
        Long subjectId,
        String subjectTitle,
        SubjectType subjectType,
        Instant voteEndAt,
        long totalCount,
        long pendingCount
) {
    public static ReminderTaskResponse from(ReminderTask task) {
        return new ReminderTaskResponse(
                task.subjectId(),
                task.subjectTitle(),
                task.subjectType(),
                task.voteEndAt(),
                task.totalCount(),
                task.pendingCount());
    }
}
