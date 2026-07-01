package com.pangu.application.voting.command;

import com.pangu.domain.model.voting.VoteChoice;

public record OfflineProxyVoteCommand(
        Long subjectId,
        Long opid,
        Long targetId,
        VoteChoice choice,
        String offlineEvidenceHash
) {
}
