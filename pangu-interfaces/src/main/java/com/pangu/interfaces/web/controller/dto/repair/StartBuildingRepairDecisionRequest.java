// 关联业务：校验楼栋/单元维修按备案议事规则启动微信接龙的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.StartBuildingRepairDecisionCommand;
import com.pangu.domain.model.repair.RepairProjectGovernance.NonResponseRule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StartBuildingRepairDecisionRequest(
        @NotNull @Min(0) Integer expectedProjectVersion,
        @NotNull Long ruleDocumentAttachmentId,
        @NotBlank @Size(max = 64) String ruleVersion,
        @NotBlank @Size(max = 1000) String deliveryRule,
        @NotNull NonResponseRule nonResponseRule,
        @NotBlank @Size(max = 200) String scopeLabel
) {
    public StartBuildingRepairDecisionCommand toCommand() {
        return new StartBuildingRepairDecisionCommand(
                expectedProjectVersion, ruleDocumentAttachmentId, ruleVersion,
                deliveryRule, nonResponseRule, scopeLabel);
    }
}
