// 关联业务：校验物业在楼栋维修决定通过后提交正式报审文件的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.SubmitBuildingRepairOfficialDocumentCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitBuildingRepairOfficialDocumentRequest(
        @NotNull @Min(0) Integer expectedProcessVersion,
        @NotNull Long attachmentId
) {
    public SubmitBuildingRepairOfficialDocumentCommand toCommand() {
        return new SubmitBuildingRepairOfficialDocumentCommand(expectedProcessVersion, attachmentId);
    }
}
