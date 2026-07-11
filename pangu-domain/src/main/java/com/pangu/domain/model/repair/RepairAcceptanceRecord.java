package com.pangu.domain.model.repair;

/** 一名验收参与人的独立结论。 */
public record RepairAcceptanceRecord(
        Long roomId,
        String participantType,
        Long participantAccountId,
        Long participantUserId,
        String participantName,
        String conclusion,
        String opinion,
        String signatureHash,
        String evidenceHash,
        Long submittedByUserId
) {
}
