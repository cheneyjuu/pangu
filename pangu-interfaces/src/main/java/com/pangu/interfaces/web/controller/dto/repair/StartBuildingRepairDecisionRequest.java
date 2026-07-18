// 关联业务：校验楼栋/单元维修按备案议事规则启动微信接龙的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.StartBuildingRepairDecisionCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StartBuildingRepairDecisionRequest(
        @NotNull @Min(0) Integer expectedProjectVersion
) {
    public StartBuildingRepairDecisionCommand toCommand() {
        return new StartBuildingRepairDecisionCommand(expectedProjectVersion);
    }
}
