package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 业委会委员差额选举计票与多阶排序筛选引擎 (领域服务)
 */
public class ElectionVotingEngine extends AbstractVotingEngine<ElectionSubject, ElectionVotingResult> {

    @Override
    protected ElectionVotingResult calculateResult(ElectionSubject subject, List<VoteItem> validVotes, 
                                                    BigDecimal totalArea, long totalOwnerCount, 
                                                    BigDecimal participatingArea, long participatingOwnerCount, 
                                                    boolean quorumSatisfied) {
        
        List<CandidateElectionResult> candidateResults = new ArrayList<>();

        if (quorumSatisfied && participatingOwnerCount > 0 && participatingArea.compareTo(BigDecimal.ZERO) > 0) {
            // 1. 针对每一个候选人分别统计得票
            for (Candidate candidate : subject.getCandidates()) {
                // 筛选出针对该候选人的赞成票
                List<VoteItem> supportVotes = validVotes.stream()
                        .filter(vote -> candidate.getCandidateId().equals(vote.getTargetId()) 
                                && vote.getChoice() == VoteChoice.SUPPORT)
                        .collect(Collectors.toList());

                // 累加赞成面积
                BigDecimal supportArea = supportVotes.stream()
                        .map(VoteItem::getPropertyArea)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // 累加赞成人数
                long supportOwnerCount = supportVotes.size();

                // 检查是否双过半 (赞成面积 > 参会总面积的 50% 且 赞成人数 > 参会总人数的 50%)
                // 精确的代数比例判定，避免除法精度截断误差
                boolean passedHalf = supportArea.multiply(new BigDecimal("2")).compareTo(participatingArea) > 0
                        && supportOwnerCount * 2 > participatingOwnerCount;

                // 计算综合分值 Score = 面积占比 + 人数占比 (用于差额选举排序)
                BigDecimal areaRatio = supportArea.divide(participatingArea, 4, RoundingMode.HALF_UP);
                BigDecimal ownerRatio = BigDecimal.valueOf(supportOwnerCount)
                        .divide(BigDecimal.valueOf(participatingOwnerCount), 4, RoundingMode.HALF_UP);
                BigDecimal score = areaRatio.add(ownerRatio);

                // 生成随机抽签值，用于极端的双平票情况下的系统仲裁 (避免频繁创建 Random)
                int drawValue = java.util.concurrent.ThreadLocalRandom.current().nextInt(10000);

                candidateResults.add(CandidateElectionResult.builder()
                        .candidate(candidate)
                        .supportArea(supportArea)
                        .supportOwnerCount(supportOwnerCount)
                        .passedHalf(passedHalf)
                        .score(score)
                        .drawValue(drawValue)
                        .build());
            }
        }

        // 2. 差额选举过滤与当选人筛选逻辑
        List<Candidate> winners = new ArrayList<>();
        if (quorumSatisfied) {
            // 过滤出所有通过双过半门槛的合格候选人
            List<CandidateElectionResult> eligibleResults = candidateResults.stream()
                    .filter(CandidateElectionResult::isPassedHalf)
                    .sorted((r1, r2) -> {
                        // A 阶排序：比较 Score 综合分
                        int cmp = r2.getScore().compareTo(r1.getScore());
                        if (cmp != 0) return cmp;
                        
                        // B 阶排序：平票时比较绝对赞成面积
                        cmp = r2.getSupportArea().compareTo(r1.getSupportArea());
                        if (cmp != 0) return cmp;
                        
                        // C 阶排序：仍平票时根据在线抽签随机值排序
                        return r2.getDrawValue().compareTo(r1.getDrawValue());
                    })
                    .collect(Collectors.toList());

            // 确定最终选出的名额上限 W
            int targetWinnerCount = Math.min(subject.getMaxWinners(), eligibleResults.size());

            if (targetWinnerCount > 0) {
                // 划分党员池和非党员池 (保持原有排序)
                List<CandidateElectionResult> partyPool = eligibleResults.stream()
                        .filter(r -> r.getCandidate().isPartyMember())
                        .collect(Collectors.toList());

                List<CandidateElectionResult> nonPartyPool = eligibleResults.stream()
                        .filter(r -> !r.getCandidate().isPartyMember())
                        .collect(Collectors.toList());

                // 党员占比原则上不低于 50% (计算出党员目标最低席位数 P)
                int targetPartyCount = (int) Math.ceil(targetWinnerCount / 2.0);

                if (partyPool.size() >= targetPartyCount) {
                    // 情况 A: 党员人选充足，先选出前 P 名党员
                    List<CandidateElectionResult> selectedParty = partyPool.subList(0, targetPartyCount);
                    winners.addAll(selectedParty.stream().map(CandidateElectionResult::getCandidate).toList());

                    // 剩余席位从剩下的所有党员与非党员中，重新按综合得分高低混合选出
                    List<CandidateElectionResult> remainingPool = new ArrayList<>();
                    remainingPool.addAll(partyPool.subList(targetPartyCount, partyPool.size()));
                    remainingPool.addAll(nonPartyPool);

                    // 按得分重新排序并填充剩余席位
                    remainingPool.sort((r1, r2) -> {
                        int cmp = r2.getScore().compareTo(r1.getScore());
                        if (cmp != 0) return cmp;
                        cmp = r2.getSupportArea().compareTo(r1.getSupportArea());
                        if (cmp != 0) return cmp;
                        return r2.getDrawValue().compareTo(r1.getDrawValue());
                    });

                    int remainingSeats = targetWinnerCount - targetPartyCount;
                    for (int i = 0; i < remainingSeats; i++) {
                        winners.add(remainingPool.get(i).getCandidate());
                    }
                } else {
                    // 情况 B: 合格党员人选不足 50%，此时为了避免空席，将合格党员全部选入
                    winners.addAll(partyPool.stream().map(CandidateElectionResult::getCandidate).toList());
                    
                    // 剩余的所有空缺席位由非党员池补齐
                    int remainingSeats = targetWinnerCount - partyPool.size();
                    for (int i = 0; i < remainingSeats && i < nonPartyPool.size(); i++) {
                        winners.add(nonPartyPool.get(i).getCandidate());
                    }
                }
            }
        }

        return ElectionVotingResult.builder()
                .subject(subject)
                .totalArea(totalArea)
                .totalOwnerCount(totalOwnerCount)
                .participatingArea(participatingArea)
                .participatingOwnerCount(participatingOwnerCount)
                .quorumSatisfied(quorumSatisfied)
                .passed(quorumSatisfied && !winners.isEmpty())
                .candidateResults(candidateResults)
                .winners(winners)
                .build();
    }
}
