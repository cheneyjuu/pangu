// 关联业务：校验业委会主任或副主任在线确认楼栋维修审价结果的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.ApproveBuildingRepairCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApproveBuildingRepairRequest(
        @NotNull @Min(0) Integer expectedProcessVersion,
        @Size(max = 1000) String opinion
) {
    public ApproveBuildingRepairCommand toCommand() {
        return new ApproveBuildingRepairCommand(expectedProcessVersion, opinion);
    }
}
