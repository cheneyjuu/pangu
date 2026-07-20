// 关联业务：向有权管理人员展示未反馈票的逐条认定结果与存证摘要。
package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingNonResponseDerivation;
import com.pangu.domain.model.voting.VotingNonResponsePolicy;

import java.math.BigDecimal;
import java.time.Instant;

/** 未反馈表决票认定审计行；与业主实际票明确分开。 */
public record VotingNonResponseDerivationResponse(
        Long derivationId,
        Long electorateItemId,
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
    public static VotingNonResponseDerivationResponse from(VotingNonResponseDerivation derivation) {
        return new VotingNonResponseDerivationResponse(
                derivation.derivationId(), derivation.electorateItemId(), derivation.opid(),
                derivation.uid(), derivation.propertyArea(), derivation.policy(),
                derivation.derivedChoice(), derivation.deliveryEvidenceHash(),
                derivation.ruleSnapshotHash(), derivation.reasonCode(),
                derivation.rowHash(), derivation.derivedAt());
    }
}
