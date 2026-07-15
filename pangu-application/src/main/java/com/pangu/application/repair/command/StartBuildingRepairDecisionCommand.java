// 关联业务：以备案规则快照启动楼栋/单元维修微信接龙决定。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProjectGovernance.NonResponseRule;

public record StartBuildingRepairDecisionCommand(
        Integer expectedProjectVersion,
        Long ruleDocumentAttachmentId,
        String ruleVersion,
        String deliveryRule,
        NonResponseRule nonResponseRule,
        String scopeLabel
) {
}
