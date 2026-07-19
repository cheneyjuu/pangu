// 关联业务：向管理端返回业主大会纸质选票进入统一有效票台账后的回执。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.VotingBallotRecord;

public record OwnersAssemblyVoteResponse(
        Long assemblyVoteId,
        Long packageId,
        Long subjectId,
        Long voteId,
        String voteChannel,
        boolean valid
) {
    public static OwnersAssemblyVoteResponse from(VotingBallotRecord record) {
        return new OwnersAssemblyVoteResponse(
                record.ballotId(),
                record.packageId(),
                record.subjectId(),
                record.voteId(),
                record.voteChannel().name(),
                true);
    }
}
