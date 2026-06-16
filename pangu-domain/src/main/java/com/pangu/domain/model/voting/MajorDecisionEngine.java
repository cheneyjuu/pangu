package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 重大自治决议计票引擎 (双参与且参会双3/4通过)
 * 适用于：筹集和使用专项维修资金等民法典规定的重大决策事项
 */
public class MajorDecisionEngine extends AbstractVotingEngine<VotingSubject, VotingResult<VotingSubject>> {

    @Override
    protected VotingResult<VotingSubject> calculateResult(VotingSubject subject, List<VoteItem> validVotes, 
                                                          BigDecimal totalArea, long totalOwnerCount, 
                                                          BigDecimal participatingArea, long participatingOwnerCount, 
                                                          boolean quorumSatisfied) {
        
        // 1. 基于 Stream 计算选择 SUPPORT 的专有面积总和
        BigDecimal supportArea = validVotes.stream()
                .filter(vote -> vote.getChoice() == VoteChoice.SUPPORT)
                .map(VoteItem::getPropertyArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. 计算赞成票人数
        long supportOwnerCount = validVotes.stream()
                .filter(vote -> vote.getChoice() == VoteChoice.SUPPORT)
                .count();

        boolean passed = false;

        if (quorumSatisfied && participatingOwnerCount > 0 && participatingArea.compareTo(BigDecimal.ZERO) > 0) {
            // 赞成面积比率必须达到 3/4 及以上 (>= 75%)，即 4 * supportArea >= 3 * participatingArea
            boolean areaPassed = supportArea.multiply(new BigDecimal("4"))
                    .compareTo(participatingArea.multiply(new BigDecimal("3"))) >= 0;
            // 赞成人数比率必须达到 3/4 及以上 (>= 75%)，即 4 * supportOwnerCount >= 3 * participatingOwnerCount
            boolean ownerPassed = supportOwnerCount * 4 >= participatingOwnerCount * 3;

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
                .supportArea(supportArea)
                .supportOwnerCount(supportOwnerCount)
                .build();
    }
}
