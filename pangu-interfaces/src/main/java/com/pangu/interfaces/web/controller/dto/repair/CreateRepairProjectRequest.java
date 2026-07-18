// 关联业务：校验维修工程项目范围、资金账簿、治理依据和首版实施方案输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.CreateRepairProjectCommand;
import com.pangu.domain.model.repair.RepairProject.FundSource;
import com.pangu.domain.model.repair.RepairProject.GovernancePath;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRepairProjectRequest(
        @NotBlank @Size(max = 160) String projectName,
        @NotNull ScopeType scopeType,
        Long buildingId,
        @Size(max = 64) String unitName,
        @NotNull FundSource fundSource,
        @NotNull GovernancePath governancePath,
        @Valid @NotNull RepairPlanRequest plan
) {
    public CreateRepairProjectCommand toCommand() {
        return new CreateRepairProjectCommand(
                projectName, scopeType, buildingId, unitName, fundSource, governancePath, plan.toCommand());
    }
}
