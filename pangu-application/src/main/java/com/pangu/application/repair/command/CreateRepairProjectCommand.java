// 关联业务：创建单一决定范围下的维修工程筹备草稿；资金、治理和验收依据不从范围推导。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProject.ScopeType;

public record CreateRepairProjectCommand(
        String projectName,
        ScopeType scopeType,
        Long buildingId,
        String unitName,
        RepairPlanDraftCommand plan
) {
}
