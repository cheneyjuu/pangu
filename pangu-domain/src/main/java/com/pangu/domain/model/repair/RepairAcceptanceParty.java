// 关联业务：保存一名验收参与人的身份、签署方式、结论和原始证据引用。
package com.pangu.domain.model.repair;

public record RepairAcceptanceParty(
        String participantKey,
        RepairAcceptancePartyRole partyRole,
        Long roomId,
        Long ownerUid,
        Long participantAccountId,
        Long participantUserId,
        String participantName,
        String participantOrganization,
        String committeePosition,
        RepairAcceptanceConclusion conclusion,
        String opinion,
        String submissionMethod,
        String signatureHash,
        String evidenceHash,
        Long sealUsageId,
        Long submittedByUserId
) {
}
