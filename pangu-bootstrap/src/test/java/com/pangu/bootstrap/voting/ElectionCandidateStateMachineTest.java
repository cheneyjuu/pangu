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
 * <p>覆盖 {@link ElectionCandidateActions} 的两段化合法迁移
 * （党组前置审查 PENDING_PARTY_REVIEW → PENDING_COMMITTEE_REVIEW → APPROVED）、
 * 跨阶段防护（跳过党组前置审查直接资格通过 / 资格阶段调党组驳回）、终态再迁移防护，
 * 以及 {@link CandidateStatus#fromDbValue} 的边界。
 */
public class ElectionCandidateStateMachineTest {

    private Candidate at(CandidateStatus status) {
        return Candidate.builder()
                .candidateId(1L)
                .subjectId(7001L)
                .uid(70001L)
                .name("张三")
                .partyMember(true)
                .qualificationStatus(status)
                .build();
    }

    // ===== 党组书记前置审查阶段（PENDING_PARTY_REVIEW）=====

    @Test
    public void partyApprove_partyToCommittee() {
        Candidate c = at(CandidateStatus.PENDING_PARTY_REVIEW);
        ElectionCandidateActions.partyApprove(c);
        assertEquals(CandidateStatus.PENDING_COMMITTEE_REVIEW, c.getQualificationStatus());
    }

    @Test
    public void partyReject_partyToRejected() {
        Candidate c = at(CandidateStatus.PENDING_PARTY_REVIEW);
        ElectionCandidateActions.partyReject(c);
        assertEquals(CandidateStatus.REJECTED, c.getQualificationStatus());
    }

    @Test
    public void partyApprove_onCommitteeStage_throws() {
        // 已过党组前置审查的候选人再次前置审查 → 非法。
        Candidate c = at(CandidateStatus.PENDING_COMMITTEE_REVIEW);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.partyApprove(c));
    }

    @Test
    public void partyReject_onCommitteeStage_throws() {
        // 居委会资格审查阶段调党组驳回 → 源态守卫拒绝。
        Candidate c = at(CandidateStatus.PENDING_COMMITTEE_REVIEW);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.partyReject(c));
    }

    // ===== 居委会资格审查阶段（PENDING_COMMITTEE_REVIEW）=====

    @Test
    public void approve_committeeToApproved() {
        Candidate c = at(CandidateStatus.PENDING_COMMITTEE_REVIEW);
        ElectionCandidateActions.approve(c);
        assertEquals(CandidateStatus.APPROVED, c.getQualificationStatus());
    }

    @Test
    public void reject_committeeToRejected() {
        Candidate c = at(CandidateStatus.PENDING_COMMITTEE_REVIEW);
        ElectionCandidateActions.reject(c);
        assertEquals(CandidateStatus.REJECTED, c.getQualificationStatus());
    }

    @Test
    public void approve_skipsPartyPreReview_throws() {
        // 仍在党组前置审查阶段，直接居委会资格通过 → 必须先过党组前置审查。
        Candidate c = at(CandidateStatus.PENDING_PARTY_REVIEW);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.approve(c));
    }

    @Test
    public void reject_onPartyStage_throws() {
        // 居委会资格驳回要求源态=PENDING_COMMITTEE_REVIEW，党组阶段调用被源态守卫拒绝。
        Candidate c = at(CandidateStatus.PENDING_PARTY_REVIEW);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.reject(c));
    }

    // ===== 终态防护 =====

    @Test
    public void approve_onApprovedTerminal_throws() {
        Candidate c = at(CandidateStatus.APPROVED);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.approve(c));
    }

    @Test
    public void reject_onRejectedTerminal_throws() {
        Candidate c = at(CandidateStatus.REJECTED);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.reject(c));
    }

    @Test
    public void approve_afterCommitteeReject_throws() {
        Candidate c = at(CandidateStatus.PENDING_COMMITTEE_REVIEW);
        ElectionCandidateActions.reject(c);
        assertThrows(ElectionCandidateActions.IllegalCandidateTransitionException.class,
                () -> ElectionCandidateActions.approve(c));
    }

    // ===== dbValue 往返 =====

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
