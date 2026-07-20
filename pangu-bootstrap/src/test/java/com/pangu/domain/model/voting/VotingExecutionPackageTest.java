// 关联业务：验证正式表决包只能在依据和名册冻结后收票，且渠道必须符合本次安排。
package com.pangu.domain.model.voting;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VotingExecutionPackageTest {

    private static final Instant START = Instant.parse("2026-07-20T01:00:00Z");
    private static final Instant END = Instant.parse("2026-07-21T01:00:00Z");

    @Test
    void draftRequiresRealProposalAndRuleSnapshots() {
        assertThrows(IllegalArgumentException.class, () -> VotingExecutionPackage.draft(
                10001L,
                VotingExecutionPackage.BusinessType.OWNERS_ASSEMBLY,
                9001L,
                "OWNERS_ASSEMBLY_ARRANGEMENT",
                8001L,
                null,
                "OWNERS_ASSEMBLY_RULE",
                7001L,
                hash('b'),
                VotingScope.COMMUNITY,
                null,
                VotingExecutionPackage.CollectionMode.PAPER,
                VotingExecutionPackage.DuplicateBallotPolicy.NOT_APPLICABLE,
                START,
                END,
                6001L));
    }

    @Test
    void packageCannotOpenBeforeElectorateIsFrozen() {
        VotingExecutionPackage ballotPackage = completeDraft();

        assertThrows(IllegalStateException.class, () -> ballotPackage.open(START, 6001L));

        ballotPackage.freeze(5001L, hash('d'), 6001L, START.minusSeconds(60));
        ballotPackage.open(START, 6001L);

        assertEquals(VotingExecutionPackage.Status.VOTING, ballotPackage.getStatus());
        assertEquals(5001L, ballotPackage.getElectorateSnapshotId());
    }

    @Test
    void onlinePrimaryKeepsPaperAssistanceWhilePaperModeRejectsOnlineVotes() {
        VotingExecutionPackage paper = completeDraft();
        assertTrue(paper.accepts(VoteChannel.PAPER));
        assertFalse(paper.accepts(VoteChannel.ONLINE));

        VotingExecutionPackage online = VotingExecutionPackage.draft(
                10001L,
                VotingExecutionPackage.BusinessType.OWNERS_ASSEMBLY,
                9002L,
                "OWNERS_ASSEMBLY_ARRANGEMENT",
                8002L,
                hash('a'),
                "OWNERS_ASSEMBLY_RULE",
                7002L,
                hash('b'),
                VotingScope.COMMUNITY,
                null,
                VotingExecutionPackage.CollectionMode.ONLINE_WITH_PAPER_ASSISTANCE,
                VotingExecutionPackage.DuplicateBallotPolicy.NOT_APPLICABLE,
                START,
                END,
                6001L);

        assertTrue(online.accepts(VoteChannel.ONLINE));
        assertTrue(online.accepts(VoteChannel.PAPER));
    }

    private VotingExecutionPackage completeDraft() {
        return VotingExecutionPackage.draft(
                10001L,
                VotingExecutionPackage.BusinessType.OWNERS_ASSEMBLY,
                9001L,
                "OWNERS_ASSEMBLY_ARRANGEMENT",
                8001L,
                hash('a'),
                "OWNERS_ASSEMBLY_RULE",
                7001L,
                hash('b'),
                VotingScope.COMMUNITY,
                null,
                VotingExecutionPackage.CollectionMode.PAPER,
                VotingExecutionPackage.DuplicateBallotPolicy.NOT_APPLICABLE,
                START,
                END,
                6001L);
    }

    private String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
