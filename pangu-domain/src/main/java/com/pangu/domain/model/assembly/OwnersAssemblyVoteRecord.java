package com.pangu.domain.model.assembly;

import java.time.Instant;

/** 业主大会投票审计记录，关联真正参与计票的 t_vote_item。 */
public record OwnersAssemblyVoteRecord(
        Long assemblyVoteId,
        Long packageId,
        Long subjectId,
        Long voteId,
        Long tenantId,
        Long opid,
        Long uid,
        String voteChannel,
        String packageHash,
        String ballotFileHash,
        String signatureHash,
        boolean valid,
        Long invalidatedByVoteId,
        String invalidReason,
        Instant createTime
) {
}
