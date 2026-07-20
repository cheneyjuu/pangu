// 关联业务：将纸质或线上票据证据与真正参与计票的结构化有效票建立可审计对应关系。
package com.pangu.domain.model.voting;

import java.time.Instant;

/** 通用票据台账；跨渠道唯一性由表决事项和冻结名册专有部分共同约束。 */
public record VotingBallotRecord(
        Long ballotId,
        Long packageId,
        Long subjectId,
        Long voteId,
        Long electorateItemId,
        Long tenantId,
        Long opid,
        Long uid,
        VoteChannel voteChannel,
        String packageHash,
        String ballotFileHash,
        String signatureHash,
        Long recordedByUserId,
        Instant castAt,
        Long supersedesBallotId,
        VotingExecutionPackage.DuplicateBallotPolicy resolutionPolicy,
        String resolutionReason
) {
}
