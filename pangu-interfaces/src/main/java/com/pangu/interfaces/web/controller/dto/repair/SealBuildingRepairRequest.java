// 关联业务：校验获授权业委会成员登记楼栋维修正式盖章文件的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.SealBuildingRepairCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SealBuildingRepairRequest(
        @NotNull @Min(0) Integer expectedProcessVersion,
        @NotNull Long sealedAttachmentId,
        @Size(max = 500) String remark
) {
    public SealBuildingRepairCommand toCommand() {
        return new SealBuildingRepairCommand(expectedProcessVersion, sealedAttachmentId, remark);
    }
}
