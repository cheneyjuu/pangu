// 关联业务：由物业或有建项权限主体提出维修工程责任、资金承担和执行依据认定。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProject.ExecutionAuthorityType;
import com.pangu.domain.model.repair.RepairProject.FundingSourceType;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityPath;

import java.math.BigDecimal;

public record ProposeRepairResponsibilityDeterminationCommand(
        Integer expectedProjectVersion,
        ResponsibilityPath responsibilityPath,
        FundingSourceType fundingSourceType,
        ExecutionAuthorityType executionAuthorityType,
        Long basisAttachmentId,
        String basisReference,
        String responsiblePartyName,
        String responsiblePartyReference,
        BigDecimal approvedAmount
) {
}
