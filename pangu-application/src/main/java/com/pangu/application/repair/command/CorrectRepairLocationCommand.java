package com.pangu.application.repair.command;

import java.util.List;

public record CorrectRepairLocationCommand(
        Long buildingId,
        Long roomId,
        String locationText,
        String reason,
        String fieldSupplement,
        List<Long> evidenceImageAttachmentIds
) {
}
