package com.pangu.application.repair.command;

public record StartRepairLocalDecisionCommand(
        String scopeType,
        String decisionChannel,
        String unitName,
        String scopeLabel,
        String remark
) {
}
