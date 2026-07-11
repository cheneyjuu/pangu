package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.VoteChoice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CastAssemblyOnlineVoteRequest(
        @NotNull Long subjectId,
        @NotNull Long opid,
        @NotNull VoteChoice choice,
        @NotBlank @Size(max = 128) String ballotFileHash,
        @NotBlank @Size(max = 256) String signatureHash
) {
}
