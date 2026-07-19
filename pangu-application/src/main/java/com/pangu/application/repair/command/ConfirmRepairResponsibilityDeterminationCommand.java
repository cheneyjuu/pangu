// 关联业务：由有治理权限的主体确认维修工程责任、资金承担和执行依据。
package com.pangu.application.repair.command;

public record ConfirmRepairResponsibilityDeterminationCommand(
        Integer expectedProjectVersion,
        String confirmationNote
) {
}
