package com.pangu.application.repair.command;

public record EvaluateRepairCommand(
        Integer satisfactionScore,
        String comment
) {
}
