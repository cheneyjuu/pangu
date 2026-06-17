package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 业委会委员差额选举计票与多阶排序筛选引擎（领域服务）。
 *
 * <p>党员比例下限改造：原硬编码 {@code Math.ceil(targetWinnerCount / 2.0)} 改为
 * 从 {@link VotingSubject#getEffectivePartyRatioFloor()} 读取，由 application 层
 * 根据放宽 waiver 的断路器结果决定（默认 0.50；放宽通过且未触发自动撤销时为更低值）。
 */
public class ElectionVotingEngine extends AbstractVotingEngine<ElectionSubject, ElectionVotingResult> {

    @Override
    protected ElectionVotingResult calculateResult(ElectionSubject subject, List<VoteItem> validVotes,
                                                    BigDecimal totalArea, long totalOwnerCount,
                                                    BigDecimal participatingArea, long participatingOwnerCount,
                                                    boolean quorumSatisfied) {

        List<CandidateElectionResult> candidateResults = new ArrayList<>();

        if (quorumSatisfied && participatingOwnerCount > 0 && participatingArea.compareTo(BigDecimal.ZERO) > 0) {
            for (Candidate candidate : subject.getCandidates()) {
                List<VoteItem> supportVotes = validVotes.stream()
                        .filter(vote -> candidate.getCandidateId().equals(vote.getTargetId())
                                && vote.getChoice() == VoteChoice.SUPPORT)
                        .collect(Collectors.toList());

                BigDecimal supportArea = supportVotes.stream()
                        .map(VoteItem::getPropertyArea)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                long supportOwnerCount = supportVotes.size();

                // 双过半判定：精确代数比较，避免除法精度截断
                boolean passedHalf = supportArea.multiply(new BigDecimal("2")).compareTo(participatingArea) > 0
                        && supportOwnerCount * 2 > participatingOwnerCount;

                BigDecimal areaRatio = supportArea.divide(participatingArea, 4, RoundingMode.HALF_UP);
                BigDecimal ownerRatio = BigDecimal.valueOf(supportOwnerCount)
                        .divide(BigDecimal.valueOf(participatingOwnerCount), 4, RoundingMode.HALF_UP);
                BigDecimal score = areaRatio.add(ownerRatio);

                int drawValue = ThreadLocalRandom.current().nextInt(10000);

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

        List<Candidate> winners = new ArrayList<>();
        if (quorumSatisfied) {
            List<CandidateElectionResult> eligibleResults = candidateResults.stream()
                    .filter(CandidateElectionResult::isPassedHalf)
                    .sorted(this::compareByMultiTier)
                    .collect(Collectors.toList());

            int targetWinnerCount = Math.min(subject.getMaxWinners(), eligibleResults.size());

            if (targetWinnerCount > 0) {
                List<CandidateElectionResult> partyPool = eligibleResults.stream()
                        .filter(r -> r.getCandidate().isPartyMember())
                        .collect(Collectors.toList());

                List<CandidateElectionResult> nonPartyPool = eligibleResults.stream()
                        .filter(r -> !r.getCandidate().isPartyMember())
                        .collect(Collectors.toList());

                // 党员席位下限：CEILING(targetWinnerCount * effectiveRatio)
                BigDecimal effectiveRatio = subject.getEffectivePartyRatioFloor();
                int targetPartyCount = effectiveRatio
                        .multiply(BigDecimal.valueOf(targetWinnerCount))
                        .setScale(0, RoundingMode.CEILING)
                        .intValue();

                if (partyPool.size() >= targetPartyCount) {
                    // 情况 A：党员人选充足，先选前 P 名党员
                    List<CandidateElectionResult> selectedParty = partyPool.subList(0, targetPartyCount);
                    winners.addAll(selectedParty.stream().map(CandidateElectionResult::getCandidate).toList());

                    // 剩余席位从「剩下党员 + 全部非党员」中按综合得分混合选出
                    List<CandidateElectionResult> remainingPool = new ArrayList<>();
                    remainingPool.addAll(partyPool.subList(targetPartyCount, partyPool.size()));
                    remainingPool.addAll(nonPartyPool);
                    remainingPool.sort(this::compareByMultiTier);

                    int remainingSeats = targetWinnerCount - targetPartyCount;
                    for (int i = 0; i < remainingSeats && i < remainingPool.size(); i++) {
                        winners.add(remainingPool.get(i).getCandidate());
                    }
                } else {
                    // 情况 B：合格党员不足下限，全部党员入选 + 非党员补齐
                    winners.addAll(partyPool.stream().map(CandidateElectionResult::getCandidate).toList());

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

    /**
     * 多阶排序：A 阶 score 降序 → B 阶 supportArea 降序 → C 阶 drawValue 降序。
     */
    private int compareByMultiTier(CandidateElectionResult r1, CandidateElectionResult r2) {
        int cmp = r2.getScore().compareTo(r1.getScore());
        if (cmp != 0) return cmp;
        cmp = r2.getSupportArea().compareTo(r1.getSupportArea());
        if (cmp != 0) return cmp;
        return r2.getDrawValue().compareTo(r1.getDrawValue());
    }
}
