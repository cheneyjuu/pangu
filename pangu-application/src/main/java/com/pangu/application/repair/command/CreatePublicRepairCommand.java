package com.pangu.application.repair.command;

public record CreatePublicRepairCommand(
        Long buildingId,
        String locationText,
        String title,
        String description,
        String category,
        String evidenceText
) {
}
