// 关联业务：由业委会主任或副主任在线确认楼栋维修审价结果。
package com.pangu.application.repair.command;

public record ApproveBuildingRepairCommand(
        Integer expectedProcessVersion,
        String opinion
) {
}
