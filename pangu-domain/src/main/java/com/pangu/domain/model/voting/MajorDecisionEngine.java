package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 重大自治决议计票引擎 (双参与且参会双3/4通过)
 * 适用于：筹集和使用专项维修资金等民法典规定的重大决策事项
 */
public class MajorDecisionEngine extends AbstractVotingEngine<VotingSubject, VotingResult<VotingSubject>> {

    private static final BigDecimal THREE_QUARTERS_THRESHOLD = new BigDecimal("0.75");

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
            // 赞成面积比率必须达到 3/4 极其以上 (>= 75%)
            BigDecimal supportAreaRatio = supportArea.divide(participatingArea, 4, RoundingMode.HALF_UP);
            // 赞成人数比率必须达到 3/4 极其以上 (>= 75%)
            BigDecimal supportOwnerRatio = BigDecimal.valueOf(supportOwnerCount)
                    .divide(BigDecimal.valueOf(participatingOwnerCount), 4, RoundingMode.HALF_UP);

            passed = supportAreaRatio.compareTo(THREE_QUARTERS_THRESHOLD) >= 0 
                    && supportOwnerRatio.compareTo(THREE_QUARTERS_THRESHOLD) >= 0;
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
