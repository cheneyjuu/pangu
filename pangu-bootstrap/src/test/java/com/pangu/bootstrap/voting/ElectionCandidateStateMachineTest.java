package com.pangu.bootstrap.voting;

import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidateStatus;
import com.pangu.domain.model.voting.ElectionCandidateActions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 候选人资格状态机纯领域测试（无 Spring）。
 *
 * <p>覆盖 {@link ElectionCandidateActions} 的合法迁移、终态再迁移防护，
 * 以及 {@link CandidateStatus#fromDbValue} 的边界。
 */
public class ElectionCandidateStateMachineTest {

    private Candidate pending() {
        return Candidate.builder()
                .candidateId(1L)
                .subjectId(7001L)
                .uid(70001L)
                .name("张三")
                .partyMember(true)
                .qualificationStatus(CandidateStatus.PENDING_REVIEW)
                .build();
    }

    @Test
    public void approve_pendingToApproved() {
        Candidate c = pending();
        ElectionCandidateActions.approve(c);
        assertEquals(CandidateStatus.APPROVED, c.getQualificationStatus());
    }

    @Test
    public void reject_pendingToRejected() {
        Candidate c = pending();
        ElectionCandidateActions.reject(c);
        assertEquals(CandidateStatus.REJECTED, c.getQualificationStatus());
    }

    @Test
    public void approve_onApprovedTerminal_throws() {
        Candidate c = pending();
        c.setQualificationStatus(CandidateStatus.APPROVED);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.approve(c));
    }

    @Test
    public void reject_onRejectedTerminal_throws() {
        Candidate c = pending();
        c.setQualificationStatus(CandidateStatus.REJECTED);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.reject(c));
    }

    @Test
    public void approve_afterReject_throws() {
        Candidate c = pending();
        ElectionCandidateActions.reject(c);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.approve(c));
    }

    @Test
    public void fromDbValue_roundTrip() {
        for (CandidateStatus status : CandidateStatus.values()) {
            assertEquals(status, CandidateStatus.fromDbValue(status.getDbValue()));
        }
    }

    @Test
    public void fromDbValue_unknown_throws() {
        assertThrows(IllegalArgumentException.class, () -> CandidateStatus.fromDbValue(99));
    }
}
