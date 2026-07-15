// 关联业务：记录楼栋业主侧或全小区业委会侧的一名真实验收参与人。
package com.pangu.application.repair.command;

public record RecordRepairAcceptanceCommand(
        Long roomId,
        Long ownerUid,
        String participantType,
        String participantName,
        String participantOrganization,
        String conclusion,
        String opinion,
        String signatureHash,
        String evidenceHash,
        String remark
) {
}
