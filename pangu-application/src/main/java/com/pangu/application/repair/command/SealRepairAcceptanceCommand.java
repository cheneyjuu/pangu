// 关联业务：登记全小区公共维修验收文件的业委会用印动作。
package com.pangu.application.repair.command;

public record SealRepairAcceptanceCommand(
        Long sealedAttachmentId,
        String remark
) {
}
