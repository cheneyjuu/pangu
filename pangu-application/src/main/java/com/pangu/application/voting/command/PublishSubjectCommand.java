package com.pangu.application.voting.command;

/**
 * 议题公示命令（M3-2 DRAFT → PUBLISHED）。
 */
public record PublishSubjectCommand(
        Long subjectId,
        Long currentUserId
) {
}
