package com.pangu.application.voting.command;

/**
 * 触发结算命令。
 *
 * @param subjectId      议题 ID
 * @param triggerSource  触发来源（{@code SCHEDULER} / {@code MANUAL}），用于审计
 */
public record SettleSubjectCommand(
        Long subjectId,
        String triggerSource
) {
    public SettleSubjectCommand {
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId must not be null");
        }
        if (triggerSource == null || triggerSource.isBlank()) {
            triggerSource = "MANUAL";
        }
    }
}
