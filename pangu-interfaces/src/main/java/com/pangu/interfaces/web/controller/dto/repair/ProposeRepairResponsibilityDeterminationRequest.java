// 关联业务：接收维修工程责任和资金承担初判；执行状态由服务端派生，确认权不在该请求中授予。
package com.pangu.interfaces.web.controller.dto.repair;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pangu.application.repair.command.ProposeRepairResponsibilityDeterminationCommand;
import com.pangu.domain.model.repair.RepairProject.FundingSourceType;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityPath;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 兼容已发布客户端的废弃字段，但绝不让客户端提交的执行依据或初判金额影响服务端的业务判断。
 */
@JsonIgnoreProperties(value = {"executionAuthorityType", "approvedAmount"})
public record ProposeRepairResponsibilityDeterminationRequest(
        @NotNull @Min(0) Integer expectedProjectVersion,
        @NotNull ResponsibilityPath responsibilityPath,
        @NotNull FundingSourceType fundingSourceType,
        @NotNull Long basisAttachmentId,
        @NotBlank @Size(max = 1000) String basisReference,
        @Size(max = 200) String responsiblePartyName,
        @Size(max = 256) String responsiblePartyReference
) {
    public ProposeRepairResponsibilityDeterminationCommand toCommand() {
        return new ProposeRepairResponsibilityDeterminationCommand(
                expectedProjectVersion, responsibilityPath, fundingSourceType,
                basisAttachmentId, basisReference, responsiblePartyName, responsiblePartyReference);
    }
}
