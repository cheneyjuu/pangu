package com.pangu.application.repair.command;

public record StartRepairAssemblyDecisionCommand(
        Long packageId,
        String remark
) {
}
