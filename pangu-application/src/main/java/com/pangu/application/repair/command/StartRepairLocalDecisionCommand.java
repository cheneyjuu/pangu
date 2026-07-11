package com.pangu.application.repair.command;

public record StartRepairLocalDecisionCommand(
        String scopeType,
        String unitName,
        String scopeLabel,
        String remark
) {
}
