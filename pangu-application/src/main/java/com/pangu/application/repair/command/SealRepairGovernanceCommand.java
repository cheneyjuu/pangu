// 关联业务：提交维修报审盖章动作，区分已盖章文件上传与平台电子签章。
package com.pangu.application.repair.command;

public record SealRepairGovernanceCommand(
        String sealingMethod,
        Long sealedAttachmentId,
        Long electronicSealId,
        String remark
) {
}
