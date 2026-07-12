package com.pangu.application.repair.command;

public record CreatePublicRepairCommand(
        String publicAreaScope,
        Long buildingId,
        String locationText,
        String title,
        String description,
        String category,
        String evidenceText
) {
}
