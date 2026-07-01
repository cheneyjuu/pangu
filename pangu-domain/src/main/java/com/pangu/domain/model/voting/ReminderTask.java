package com.pangu.domain.model.voting;

import java.time.Instant;

public record ReminderTask(
        Long subjectId,
        String subjectTitle,
        SubjectType subjectType,
        Instant voteEndAt,
        long totalCount,
        long pendingCount
) {
}
