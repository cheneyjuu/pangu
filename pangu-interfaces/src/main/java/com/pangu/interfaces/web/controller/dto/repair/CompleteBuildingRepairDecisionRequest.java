// 关联业务：接收物业对楼栋维修征询结果的渠道化核验输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.CompleteBuildingRepairDecisionCommand;
import com.pangu.domain.model.repair.RepairProjectGovernance.GovernanceResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CompleteBuildingRepairDecisionRequest(
        @NotNull @Min(0) Integer expectedProcessVersion,
        Long evidenceAttachmentId,
        GovernanceResult confirmedResult
) {
    public CompleteBuildingRepairDecisionCommand toCommand() {
        return new CompleteBuildingRepairDecisionCommand(
                expectedProcessVersion, evidenceAttachmentId, confirmedResult);
    }
}
