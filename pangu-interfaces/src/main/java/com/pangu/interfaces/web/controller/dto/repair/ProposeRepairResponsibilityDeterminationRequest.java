// 关联业务：接收维修工程责任、资金承担和执行依据的提出请求；确认权不在该请求中授予。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.ProposeRepairResponsibilityDeterminationCommand;
import com.pangu.domain.model.repair.RepairProject.ExecutionAuthorityType;
import com.pangu.domain.model.repair.RepairProject.FundingSourceType;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityPath;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProposeRepairResponsibilityDeterminationRequest(
        @NotNull @Min(0) Integer expectedProjectVersion,
        @NotNull ResponsibilityPath responsibilityPath,
        @NotNull FundingSourceType fundingSourceType,
        @NotNull ExecutionAuthorityType executionAuthorityType,
        @NotNull Long basisAttachmentId,
        @NotBlank @Size(max = 1000) String basisReference,
        @Size(max = 200) String responsiblePartyName,
        @Size(max = 256) String responsiblePartyReference,
        @NotNull @DecimalMin(value = "0.01") BigDecimal approvedAmount
) {
    public ProposeRepairResponsibilityDeterminationCommand toCommand() {
        return new ProposeRepairResponsibilityDeterminationCommand(
                expectedProjectVersion, responsibilityPath, fundingSourceType, executionAuthorityType,
                basisAttachmentId, basisReference, responsiblePartyName, responsiblePartyReference, approvedAmount);
    }
}
