package com.pangu.application.repair.command;

public record AssignRepairCommand(
        Long assignedUserId,
        String assigneeRoleKey,
        String remark
) {
}
