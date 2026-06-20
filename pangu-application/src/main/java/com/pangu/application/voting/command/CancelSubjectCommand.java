package com.pangu.application.voting.command;

/**
 * 议题撤回命令（M3-2）。
 *
 * <p>{@code byGovernment=true} 表示政府强撤路径（PUBLISHED → CANCELLED，需 GOV_SUPER_ADMIN）；
 * 否则为发起者本人撤回（DRAFT → CANCELLED）。
 */
public record CancelSubjectCommand(
        Long subjectId,
        Long currentUserId,
        String reason,
        boolean byGovernment
) {
}
