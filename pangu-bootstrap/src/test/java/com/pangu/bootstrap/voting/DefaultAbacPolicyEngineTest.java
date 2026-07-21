// 关联业务：验证共同决定与业委会选举采用不同的线上实名等级边界。
package com.pangu.bootstrap.voting;

import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
import com.pangu.domain.policy.impl.DefaultAbacPolicyEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultAbacPolicyEngineTest {

    private final DefaultAbacPolicyEngine policy = new DefaultAbacPolicyEngine();

    @Test
    void commonDecisionAcceptsL2ButRejectsL1() {
        EvaluationResult l1 = policy.evaluateVoting(
                1L, 10001L, AbacPolicyEngine.VotingPurpose.COMMON_DECISION, AuthenticationLevel.L1);
        EvaluationResult l2 = policy.evaluateVoting(
                1L, 10001L, AbacPolicyEngine.VotingPurpose.COMMON_DECISION, AuthenticationLevel.L2);

        assertFalse(l1.isAllowed());
        assertTrue(l2.isAllowed());
    }

    @Test
    void committeeElectionRejectsL2ButAcceptsL3() {
        EvaluationResult l2 = policy.evaluateVoting(
                1L, 10001L, AbacPolicyEngine.VotingPurpose.COMMITTEE_ELECTION, AuthenticationLevel.L2);
        EvaluationResult l3 = policy.evaluateVoting(
                1L, 10001L, AbacPolicyEngine.VotingPurpose.COMMITTEE_ELECTION, AuthenticationLevel.L3);

        assertFalse(l2.isAllowed());
        assertTrue(l3.isAllowed());
    }
}
