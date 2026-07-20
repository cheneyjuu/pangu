// 关联业务：依据实际有效票确定多数意见，并为符合条件的未反馈表决权生成独立认定票。
package com.pangu.domain.model.voting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 未反馈票认定的纯领域服务，不读取实时名册或数据库。 */
public class NonResponseVoteResolver {

    public Resolution resolve(VotingNonResponsePolicy policy,
                              List<CountedVote> actualVotes,
                              List<EligibleNonResponse> eligibleNonResponses) {
        if (policy == null || actualVotes == null || eligibleNonResponses == null) {
            throw new IllegalArgumentException("未反馈认定输入不完整");
        }
        ensureActualVotes(actualVotes);
        if (policy == VotingNonResponsePolicy.NOT_PARTICIPATED || eligibleNonResponses.isEmpty()) {
            return new Resolution(policy, null, List.of());
        }

        VoteChoice derivedChoice = policy == VotingNonResponsePolicy.ABSTAIN
                ? VoteChoice.ABSTAIN
                : determineMajority(actualVotes);
        List<CountedVote> deemedVotes = eligibleNonResponses.stream()
                .map(candidate -> CountedVote.deemed(
                        candidate.opid(), candidate.uid(), candidate.propertyArea(),
                        derivedChoice, candidate.sourceReference()))
                .toList();
        return new Resolution(policy,
                policy == VotingNonResponsePolicy.FOLLOW_MAJORITY ? derivedChoice : null,
                deemedVotes);
    }

    private VoteChoice determineMajority(List<CountedVote> actualVotes) {
        if (actualVotes.isEmpty()) {
            throw new IndeterminateMajorityException("没有实际有效票，无法确定多数意见");
        }
        Map<VoteChoice, BigDecimal> areas = new EnumMap<>(VoteChoice.class);
        Map<VoteChoice, Set<Long>> owners = new EnumMap<>(VoteChoice.class);
        Map<VoteChoice, Set<String>> properties = new EnumMap<>(VoteChoice.class);
        Map<Long, VoteChoice> ownerChoices = new HashMap<>();
        for (VoteChoice choice : VoteChoice.values()) {
            areas.put(choice, BigDecimal.ZERO);
            owners.put(choice, new HashSet<>());
            properties.put(choice, new HashSet<>());
        }
        for (CountedVote vote : actualVotes) {
            VoteChoice previousChoice = ownerChoices.putIfAbsent(vote.uid(), vote.choice());
            if (previousChoice != null && previousChoice != vote.choice()) {
                throw new IndeterminateMajorityException(
                        "同一表决人的实际票存在不同意见，无法确定人数多数意见");
            }
            String propertyKey = vote.uid() + "-" + vote.opid();
            if (properties.get(vote.choice()).add(propertyKey)) {
                areas.put(vote.choice(), areas.get(vote.choice()).add(vote.propertyArea()));
            }
            owners.get(vote.choice()).add(vote.uid());
        }

        Map<VoteChoice, Long> ownerCounts = new EnumMap<>(VoteChoice.class);
        owners.forEach((choice, uids) -> ownerCounts.put(choice, (long) uids.size()));
        VoteChoice ownerLeader = uniqueLeader(ownerCounts);
        VoteChoice areaLeader = uniqueLeader(areas);
        if (ownerLeader != areaLeader) {
            throw new IndeterminateMajorityException("实际票的人数多数与面积多数不一致，无法确定多数意见");
        }
        return ownerLeader;
    }

    private <T extends Comparable<T>> VoteChoice uniqueLeader(Map<VoteChoice, T> tallies) {
        List<Map.Entry<VoteChoice, T>> ordered = new ArrayList<>(tallies.entrySet());
        ordered.sort(Map.Entry.<VoteChoice, T>comparingByValue(Comparator.reverseOrder()));
        if (ordered.size() < 2 || ordered.get(0).getValue().compareTo(ordered.get(1).getValue()) == 0) {
            throw new IndeterminateMajorityException("实际票存在并列最高选项，无法确定多数意见");
        }
        return ordered.getFirst().getKey();
    }

    private void ensureActualVotes(List<CountedVote> actualVotes) {
        if (actualVotes.stream().anyMatch(vote -> vote.origin() != CountedVote.Origin.ACTUAL_BALLOT)) {
            throw new IllegalArgumentException("多数意见只能依据实际有效票计算");
        }
    }

    public record EligibleNonResponse(
            Long electorateItemId,
            Long opid,
            Long uid,
            BigDecimal propertyArea,
            String sourceReference
    ) {
    }

    public record Resolution(
            VotingNonResponsePolicy policy,
            VoteChoice majorityChoice,
            List<CountedVote> deemedVotes
    ) {
        public Resolution {
            deemedVotes = deemedVotes == null ? List.of() : List.copyOf(deemedVotes);
        }
    }

    public static class IndeterminateMajorityException extends IllegalStateException {
        public IndeterminateMajorityException(String message) {
            super(message);
        }
    }
}
