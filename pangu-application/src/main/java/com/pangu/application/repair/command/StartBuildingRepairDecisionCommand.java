// 关联业务：以已登记的生效依据启动楼栋或单元维修线上实名表决。
package com.pangu.application.repair.command;

public record StartBuildingRepairDecisionCommand(
        Integer expectedProjectVersion
) {
}
