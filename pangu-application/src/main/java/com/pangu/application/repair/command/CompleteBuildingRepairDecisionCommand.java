// 关联业务：按渠道核验楼栋维修征询；微信接龙确认结果，在线表决读取系统票仓结算。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProjectGovernance.GovernanceResult;

public record CompleteBuildingRepairDecisionCommand(
        Integer expectedProcessVersion,
        Long evidenceAttachmentId,
        GovernanceResult confirmedResult
) {
}
