// 关联业务：由物业或有建项权限主体提出维修工程责任和资金承担初判；执行状态由服务端按责任路径派生。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProject.FundingSourceType;
import com.pangu.domain.model.repair.RepairProject.ResponsibilityPath;

public record ProposeRepairResponsibilityDeterminationCommand(
        Integer expectedProjectVersion,
        ResponsibilityPath responsibilityPath,
        FundingSourceType fundingSourceType,
        Long basisAttachmentId,
        String basisReference,
        String responsiblePartyName,
        String responsiblePartyReference
) {
}
