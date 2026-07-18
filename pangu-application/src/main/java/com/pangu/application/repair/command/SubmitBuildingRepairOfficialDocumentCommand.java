// 关联业务：在楼栋决定通过后归档物业正式报审文件。
package com.pangu.application.repair.command;

public record SubmitBuildingRepairOfficialDocumentCommand(
        Integer expectedProcessVersion,
        Long attachmentId
) {
}
