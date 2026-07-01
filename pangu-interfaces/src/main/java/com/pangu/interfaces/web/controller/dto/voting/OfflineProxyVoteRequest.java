package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.VoteChoice;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OfflineProxyVoteRequest(
        @NotNull Long opid,
        Long targetId,
        @NotNull VoteChoice choice,
        @Size(max = 256) String offlineEvidenceHash
) {
}
