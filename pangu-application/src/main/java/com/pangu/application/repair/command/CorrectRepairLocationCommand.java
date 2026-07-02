package com.pangu.application.repair.command;

public record CorrectRepairLocationCommand(
        Long buildingId,
        Long roomId,
        String locationText,
        String reason
) {
}
