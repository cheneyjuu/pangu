package com.pangu.application.assembly.command;

import com.pangu.domain.model.voting.VoteChoice;

public record CastAssemblyOnlineVoteCommand(
        Long packageId,
        Long subjectId,
        Long tenantId,
        Long opid,
        Long uid,
        VoteChoice choice,
        String ballotFileHash,
        String signatureHash
) {
}
