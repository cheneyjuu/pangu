package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateBallotPackageRequest(
        @NotBlank @Size(max = 32) String votingChannelPolicy,
        @Min(7) Integer publicNoticeDays,
        @NotBlank @Size(max = 128) String announcementHash,
        @NotBlank @Size(max = 128) String attachmentManifestHash,
        @NotBlank @Size(max = 128) String ballotTemplateHash,
        @Size(max = 128) String electronicSealHash,
        @NotNull Instant voteStartAt,
        @NotNull Instant voteEndAt
) {
}
