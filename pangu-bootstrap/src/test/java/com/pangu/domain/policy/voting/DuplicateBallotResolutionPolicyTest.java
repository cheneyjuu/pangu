// 关联业务：验证纸质与线上并行表决时，冻结的重复票规则在两个到达顺序下保持一致。
package com.pangu.domain.policy.voting;

import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateBallotResolutionPolicyTest {

    private final DuplicateBallotResolutionPolicy policy = new DuplicateBallotResolutionPolicy();

    @Test
    void firstValidWinsKeepsExistingBallotInBothArrivalOrders() {
        assertDecision(VotingExecutionPackage.DuplicateBallotPolicy.FIRST_VALID_WINS,
                VoteChannel.PAPER, VoteChannel.ONLINE,
                DuplicateBallotResolutionPolicy.Decision.KEEP_EXISTING);
        assertDecision(VotingExecutionPackage.DuplicateBallotPolicy.FIRST_VALID_WINS,
                VoteChannel.ONLINE, VoteChannel.PAPER,
                DuplicateBallotResolutionPolicy.Decision.KEEP_EXISTING);
    }

    @Test
    void onlinePrevailsIndependentlyOfArrivalOrder() {
        assertDecision(VotingExecutionPackage.DuplicateBallotPolicy.ONLINE_PREVAILS,
                VoteChannel.PAPER, VoteChannel.ONLINE,
                DuplicateBallotResolutionPolicy.Decision.REPLACE_EXISTING);
        assertDecision(VotingExecutionPackage.DuplicateBallotPolicy.ONLINE_PREVAILS,
                VoteChannel.ONLINE, VoteChannel.PAPER,
                DuplicateBallotResolutionPolicy.Decision.KEEP_EXISTING);
    }

    @Test
    void paperPrevailsIndependentlyOfArrivalOrder() {
        assertDecision(VotingExecutionPackage.DuplicateBallotPolicy.PAPER_PREVAILS,
                VoteChannel.ONLINE, VoteChannel.PAPER,
                DuplicateBallotResolutionPolicy.Decision.REPLACE_EXISTING);
        assertDecision(VotingExecutionPackage.DuplicateBallotPolicy.PAPER_PREVAILS,
                VoteChannel.PAPER, VoteChannel.ONLINE,
                DuplicateBallotResolutionPolicy.Decision.KEEP_EXISTING);
    }

    @Test
    void sameChannelNeverCreatesAReplacementChain() {
        assertDecision(VotingExecutionPackage.DuplicateBallotPolicy.ONLINE_PREVAILS,
                VoteChannel.ONLINE, VoteChannel.ONLINE,
                DuplicateBallotResolutionPolicy.Decision.KEEP_EXISTING);
        assertDecision(VotingExecutionPackage.DuplicateBallotPolicy.PAPER_PREVAILS,
                VoteChannel.OFFLINE_PROXY, VoteChannel.PAPER,
                DuplicateBallotResolutionPolicy.Decision.KEEP_EXISTING);
    }

    private void assertDecision(VotingExecutionPackage.DuplicateBallotPolicy duplicatePolicy,
                                VoteChannel existing,
                                VoteChannel incoming,
                                DuplicateBallotResolutionPolicy.Decision expected) {
        assertThat(policy.resolve(duplicatePolicy, existing, incoming).decision()).isEqualTo(expected);
    }
}
