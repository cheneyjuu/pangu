package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class ReminderTaskRow {
    private Long subjectId;
    private String subjectTitle;
    private Integer subjectType;
    private Instant voteEndAt;
    private Long totalCount;
    private Long pendingCount;
}
