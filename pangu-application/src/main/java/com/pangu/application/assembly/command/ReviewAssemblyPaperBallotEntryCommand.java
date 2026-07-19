// 关联业务：由不同人员对照纸票原件确认或退回一版业主大会纸票录入。
package com.pangu.application.assembly.command;

import com.pangu.application.voting.PaperVotingService;

import java.time.Instant;

public record ReviewAssemblyPaperBallotEntryCommand(
        Long sessionId,
        Long paperBallotId,
        Long entryId,
        Long tenantId,
        PaperVotingService.ReviewDecision decision,
        String reviewNote,
        Long reviewedByUserId,
        Instant reviewedAt
) {
}
