// 关联业务：由获授权业委会印章保管人登记楼栋维修正式文件用印。
package com.pangu.application.repair.command;

public record SealBuildingRepairCommand(
        Integer expectedProcessVersion,
        Long sealedAttachmentId,
        String remark
) {
}
