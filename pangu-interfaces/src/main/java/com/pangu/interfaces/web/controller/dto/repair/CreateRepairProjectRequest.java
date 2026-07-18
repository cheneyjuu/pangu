// 关联业务：校验维修工程筹备草稿的单一决定范围和首版维修点位；资金、治理和验收依据在可信来源接入后另行冻结。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.CreateRepairProjectCommand;
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
        @Valid @NotNull RepairPlanRequest plan
) {
    public CreateRepairProjectCommand toCommand() {
        return new CreateRepairProjectCommand(
                projectName, scopeType, buildingId, unitName, plan.toCommand());
    }
}
