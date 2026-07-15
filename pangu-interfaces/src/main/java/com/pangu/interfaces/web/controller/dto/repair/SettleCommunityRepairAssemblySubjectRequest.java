// 关联业务：校验全小区维修读取已结算业主大会单个事项结果的项目版本输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.SettleCommunityRepairAssemblySubjectCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SettleCommunityRepairAssemblySubjectRequest(
        @NotNull @Min(0) Integer expectedProjectVersion
) {
    public SettleCommunityRepairAssemblySubjectCommand toCommand() {
        return new SettleCommunityRepairAssemblySubjectCommand(expectedProjectVersion);
    }
}
