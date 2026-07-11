package com.pangu.application.repair.command;

import java.util.List;

public record SubmitRepairSurveyCommand(
        String surveySummary,
        String riskLevel,
        List<Long> evidenceImageAttachmentIds,
        Long evidenceVideoAttachmentId,
        String remark
) {
}
