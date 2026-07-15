// 关联业务：校验维修工程下一版实施方案及项目乐观锁版本输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.CreateRepairPlanVersionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateRepairPlanVersionRequest(
        @NotNull @Min(0) Integer expectedProjectVersion,
        @Valid @NotNull RepairPlanRequest plan
) {
    public CreateRepairPlanVersionCommand toCommand() {
        return new CreateRepairPlanVersionCommand(expectedProjectVersion, plan.toCommand());
    }
}
