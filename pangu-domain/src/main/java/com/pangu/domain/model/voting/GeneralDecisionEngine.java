package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 普通自治决议计票引擎 (双参与且参会过半数通过)
 */
public class GeneralDecisionEngine extends AbstractVotingEngine<VotingSubject, VotingResult<VotingSubject>> {

    @Override
    protected VotingResult<VotingSubject> calculateResult(VotingSubject subject, List<VoteItem> validVotes, 
                                                          BigDecimal totalArea, long totalOwnerCount, 
                                                          BigDecimal participatingArea, long participatingOwnerCount, 
                                                          boolean quorumSatisfied) {
        
        // 1. 基于 Stream 函数式计算所有选择 SUPPORT 的投票专有面积总和
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
            // 赞成面积比率必须过半数 (> 50%)，即 2 * supportArea > participatingArea
            boolean areaPassed = supportArea.multiply(new BigDecimal("2")).compareTo(participatingArea) > 0;
            // 赞成人数比率必须过半数 (> 50%)，即 2 * supportOwnerCount > participatingOwnerCount
            boolean ownerPassed = supportOwnerCount * 2 > participatingOwnerCount;

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
