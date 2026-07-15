// 关联业务：创建维修工程项目，并由后端依据范围、资金账簿和治理依据确定流程。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairProject.FundSource;
import com.pangu.domain.model.repair.RepairProject.GovernancePath;
import com.pangu.domain.model.repair.RepairProject.ScopeType;

public record CreateRepairProjectCommand(
        String projectName,
        ScopeType scopeType,
        Long buildingId,
        String unitName,
        FundSource fundSource,
        GovernancePath governancePath,
        RepairPlanDraftCommand plan
) {
}
