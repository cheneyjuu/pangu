package com.pangu.application.repair.command;

import java.util.List;

public record SubmitRepairInspectionCommand(
        String publicAreaScope,
        Long buildingId,
        Long roomId,
        String locationText,
        String fieldSupplement,
        String surveySummary,
        String riskLevel,
        List<Long> evidenceImageAttachmentIds,
        Long evidenceVideoAttachmentId,
        String remark
) {
}
