package com.pangu.application.assembly.command;

import java.time.Instant;

public record CreateBallotPackageCommand(
        Long sessionId,
        Long tenantId,
        String votingChannelPolicy,
        Integer publicNoticeDays,
        String announcementHash,
        String attachmentManifestHash,
        String ballotTemplateHash,
        String electronicSealHash,
        Instant voteStartAt,
        Instant voteEndAt
) {
}
