// 关联业务：在维修工程范围与资金路径不变的前提下，创建下一版结构化实施方案。
package com.pangu.application.repair.command;

public record CreateRepairPlanVersionCommand(
        Integer expectedProjectVersion,
        RepairPlanDraftCommand plan
) {
}
