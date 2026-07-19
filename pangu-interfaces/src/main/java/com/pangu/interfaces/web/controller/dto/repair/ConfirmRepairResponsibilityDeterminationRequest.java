// 关联业务：接收有治理权限主体对维修工程责任与资金初判的确认意见。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.ConfirmRepairResponsibilityDeterminationCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmRepairResponsibilityDeterminationRequest(
        @NotNull @Min(0) Integer expectedProjectVersion,
        @Size(max = 1000) String confirmationNote
) {
    public ConfirmRepairResponsibilityDeterminationCommand toCommand() {
        return new ConfirmRepairResponsibilityDeterminationCommand(expectedProjectVersion, confirmationNote);
    }
}
