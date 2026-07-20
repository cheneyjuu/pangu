// 关联业务：固化一条有效送达但截止未反馈表决权依据冻结议事规则形成的认定结果。
package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.time.Instant;

/** 未反馈认定的不可变逐户审计记录；它不是业主提交的选票。 */
public record VotingNonResponseDerivation(
        Long derivationId,
        Long packageId,
        Long subjectId,
        Long electorateItemId,
        Long tenantId,
        Long opid,
        Long uid,
        BigDecimal propertyArea,
        VotingNonResponsePolicy policy,
        VoteChoice derivedChoice,
        String deliveryEvidenceHash,
        String ruleSnapshotHash,
        String reasonCode,
        String rowHash,
        Instant derivedAt
) {

    public CountedVote toCountedVote() {
        return CountedVote.deemed(opid, uid, propertyArea, derivedChoice, rowHash);
    }
}
