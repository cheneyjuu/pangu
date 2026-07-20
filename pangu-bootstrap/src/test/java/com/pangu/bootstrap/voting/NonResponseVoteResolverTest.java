// 关联业务：验证未反馈表决权只有在实际票形成双维度一致多数时才可按多数意见认定。
package com.pangu.bootstrap.voting;

import com.pangu.domain.model.voting.CountedVote;
import com.pangu.domain.model.voting.NonResponseVoteResolver;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VoteItem;
import com.pangu.domain.model.voting.VotingNonResponsePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NonResponseVoteResolverTest {

    private final NonResponseVoteResolver resolver = new NonResponseVoteResolver();

    @Test
    void followMajorityUsesOnlyActualVotesAndKeepsDeemedOrigin() {
        var resolution = resolver.resolve(
                VotingNonResponsePolicy.FOLLOW_MAJORITY,
                List.of(actual(1, 101, "60", VoteChoice.SUPPORT),
                        actual(2, 102, "40", VoteChoice.AGAINST),
                        actual(3, 103, "30", VoteChoice.SUPPORT)),
                List.of(eligible(4, 104, "50"), eligible(5, 105, "20")));

        assertEquals(VoteChoice.SUPPORT, resolution.majorityChoice());
        assertEquals(2, resolution.deemedVotes().size());
        assertEquals(VoteChoice.SUPPORT, resolution.deemedVotes().getFirst().choice());
        assertEquals(CountedVote.Origin.DEEMED_NON_RESPONSE,
                resolution.deemedVotes().getFirst().origin());
    }

    @Test
    void abstainDoesNotRequireAnActualMajority() {
        var resolution = resolver.resolve(
                VotingNonResponsePolicy.ABSTAIN,
                List.of(),
                List.of(eligible(4, 104, "50")));

        assertEquals(VoteChoice.ABSTAIN, resolution.deemedVotes().getFirst().choice());
    }

    @Test
    void followMajorityStopsWhenOwnerAndAreaLeadersDiffer() {
        List<CountedVote> actualVotes = List.of(
                actual(1, 101, "10", VoteChoice.SUPPORT),
                actual(2, 102, "10", VoteChoice.SUPPORT),
                actual(3, 103, "100", VoteChoice.AGAINST));

        assertThrows(NonResponseVoteResolver.IndeterminateMajorityException.class,
                () -> resolver.resolve(
                        VotingNonResponsePolicy.FOLLOW_MAJORITY,
                        actualVotes,
                        List.of(eligible(4, 104, "50"))));
    }

    @Test
    void followMajorityStopsWhenSameRepresentativeSubmittedDifferentChoices() {
        assertThrows(NonResponseVoteResolver.IndeterminateMajorityException.class,
                () -> resolver.resolve(
                        VotingNonResponsePolicy.FOLLOW_MAJORITY,
                        List.of(
                                actual(1, 101, "60", VoteChoice.SUPPORT),
                                actual(2, 101, "40", VoteChoice.AGAINST)),
                        List.of(eligible(3, 103, "50"))));
    }

    @Test
    void followMajorityStopsOnTieOrNoActualVote() {
        assertThrows(NonResponseVoteResolver.IndeterminateMajorityException.class,
                () -> resolver.resolve(
                        VotingNonResponsePolicy.FOLLOW_MAJORITY,
                        List.of(),
                        List.of(eligible(4, 104, "50"))));
        assertThrows(NonResponseVoteResolver.IndeterminateMajorityException.class,
                () -> resolver.resolve(
                        VotingNonResponsePolicy.FOLLOW_MAJORITY,
                        List.of(
                                actual(1, 101, "50", VoteChoice.SUPPORT),
                                actual(2, 102, "50", VoteChoice.AGAINST)),
                        List.of(eligible(4, 104, "50"))));
    }

    private CountedVote actual(long opid, long uid, String area, VoteChoice choice) {
        return CountedVote.actual(new VoteItem(
                opid, uid, 1L, new BigDecimal(area), choice));
    }

    private NonResponseVoteResolver.EligibleNonResponse eligible(long itemId, long uid, String area) {
        return new NonResponseVoteResolver.EligibleNonResponse(
                itemId, itemId, uid, new BigDecimal(area), "delivery-" + itemId);
    }
}
