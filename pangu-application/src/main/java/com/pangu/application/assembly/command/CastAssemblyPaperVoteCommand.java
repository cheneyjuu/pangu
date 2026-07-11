package com.pangu.application.assembly.command;

import com.pangu.domain.model.voting.VoteChoice;

public record CastAssemblyPaperVoteCommand(
        Long packageId,
        Long subjectId,
        Long tenantId,
        Long opid,
        VoteChoice choice,
        String ballotFileHash,
        Long enteredByUserId
) {
}
