// 关联业务：按重大共同决定门槛计算表决参与情况和同意、反对、弃权汇总。
package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.util.List;

/**
 * 重大自治决议计票引擎 (双参与且参会双3/4通过)
 * 适用于：筹集和使用专项维修资金等民法典规定的重大决策事项
 */
public class MajorDecisionEngine extends AbstractVotingEngine<VotingSubject, VotingResult<VotingSubject>> {

    private static final VotingDecisionRule DEFAULT_RULE = new VotingDecisionRule(
            new VotingThreshold(2, 3, VotingThreshold.Comparison.AT_LEAST),
            new VotingThreshold(2, 3, VotingThreshold.Comparison.AT_LEAST),
            new VotingThreshold(3, 4, VotingThreshold.Comparison.AT_LEAST),
            new VotingThreshold(3, 4, VotingThreshold.Comparison.AT_LEAST));

    @Override
    protected VotingDecisionRule defaultDecisionRule() {
        return DEFAULT_RULE;
    }

    @Override
    protected VotingResult<VotingSubject> calculateResult(VotingSubject subject, List<CountedVote> validVotes,
                                                          BigDecimal totalArea, long totalOwnerCount, 
                                                          BigDecimal participatingArea, long participatingOwnerCount, 
                                                          boolean quorumSatisfied,
                                                          VotingDecisionRule decisionRule) {
        
        ChoiceTally support = tallyChoice(validVotes, VoteChoice.SUPPORT);
        ChoiceTally against = tallyChoice(validVotes, VoteChoice.AGAINST);
        ChoiceTally abstain = tallyChoice(validVotes, VoteChoice.ABSTAIN);

        boolean passed = false;

        if (quorumSatisfied && participatingOwnerCount > 0 && participatingArea.compareTo(BigDecimal.ZERO) > 0) {
            boolean areaPassed = decisionRule.approvalAreaThreshold()
                    .isSatisfied(support.area(), participatingArea);
            boolean ownerPassed = decisionRule.approvalOwnerThreshold()
                    .isSatisfied(support.ownerCount(), participatingOwnerCount);

            passed = areaPassed && ownerPassed;
        }

        return VotingResult.<VotingSubject>builder()
                .subject(subject)
                .totalArea(totalArea)
                .totalOwnerCount(totalOwnerCount)
                .participatingArea(participatingArea)
                .participatingOwnerCount(participatingOwnerCount)
                .quorumSatisfied(quorumSatisfied)
                .passed(passed)
                .supportArea(support.area())
                .supportOwnerCount(support.ownerCount())
                .againstArea(against.area())
                .againstOwnerCount(against.ownerCount())
                .abstainArea(abstain.area())
                .abstainOwnerCount(abstain.ownerCount())
                .build();
    }
}
