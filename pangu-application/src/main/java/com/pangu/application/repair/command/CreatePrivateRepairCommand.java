package com.pangu.application.repair.command;

public record CreatePrivateRepairCommand(
        Long opid,
        String title,
        String description,
        String category,
        String evidenceText
) {
}
