package com.pangu.application.repair.command;

public record RecordRepairAcceptanceCommand(
        Long roomId,
        String participantType,
        String participantName,
        String conclusion,
        String opinion,
        String signatureHash,
        String evidenceHash,
        String remark
) {
}
