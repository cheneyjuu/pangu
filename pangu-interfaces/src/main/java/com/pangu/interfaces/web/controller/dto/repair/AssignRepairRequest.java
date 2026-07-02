package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Size;

public record AssignRepairRequest(
        Long assignedUserId,
        @Size(max = 64) String assigneeRoleKey,
        @Size(max = 500) String remark
) {
}
