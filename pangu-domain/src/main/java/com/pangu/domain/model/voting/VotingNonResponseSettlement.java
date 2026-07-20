// 关联业务：向结算服务传递未反馈资格、认定明细、多数意见和审计摘要。
package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.util.List;

/** 单一事项在截止时形成的未反馈认定结果。 */
public record VotingNonResponseSettlement(
        VotingNonResponsePolicy policy,
        long eligibleOwnerCount,
        BigDecimal eligibleArea,
        VoteChoice majorityChoice,
        String derivationAggregateHash,
        List<VotingNonResponseDerivation> derivations
) {
    public VotingNonResponseSettlement {
        derivations = derivations == null ? List.of() : List.copyOf(derivations);
    }

    public List<CountedVote> deemedVotes() {
        return derivations.stream().map(VotingNonResponseDerivation::toCountedVote).toList();
    }
}
