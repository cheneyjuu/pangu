package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyVoteRecord;

public record OwnersAssemblyVoteResponse(
        Long assemblyVoteId,
        Long packageId,
        Long subjectId,
        Long voteId,
        String voteChannel,
        boolean valid
) {
    public static OwnersAssemblyVoteResponse from(OwnersAssemblyVoteRecord record) {
        return new OwnersAssemblyVoteResponse(
                record.assemblyVoteId(),
                record.packageId(),
                record.subjectId(),
                record.voteId(),
                record.voteChannel(),
                record.valid());
    }
}
