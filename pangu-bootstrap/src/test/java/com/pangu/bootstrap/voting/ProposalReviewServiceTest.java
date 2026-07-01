package com.pangu.bootstrap.voting;

import com.pangu.application.voting.ProposalReviewService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.model.voting.CandidatePoolSnapshot;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProposalReviewService} 议题级双签状态机单测。
 *
 * <p>覆盖梯度 B 的 ELECTION 主链路：
 * DRAFT → PENDING_COMMITTEE → PENDING_STREET → PUBLISHED，以及两级驳回回到 DRAFT。
 */
@ExtendWith(MockitoExtension.class)
public class ProposalReviewServiceTest {

    @Mock
    private VotingSubjectRepository subjectRepository;

    @Mock
    private ElectionCandidateRegistry electionCandidateRegistry;

    @Mock
    private UserContextHolder userContextHolder;

    @InjectMocks
    private ProposalReviewService service;

    private static final Long SUBJECT_ID = 7001L;
    private static final Long USER_ID = 800001L;

    private VotingSubject subject(SubjectStatus status) {
        return VotingSubject.builder()
                .subjectId(SUBJECT_ID)
                .tenantId(10001L)
                .title("业委会换届选举")
                .subjectType(SubjectType.ELECTION)
                .scope(VotingScope.COMMUNITY)
                .status(status)
                .voteStartAt(Instant.parse("2026-07-01T00:00:00Z"))
                .voteEndAt(Instant.parse("2026-07-15T00:00:00Z"))
                .proposedByUserId(800005L)
                .maxWinners(3)
                .version(7L)
                .build();
    }

    private void mockSubject(VotingSubject subject) {
        when(subjectRepository.findByIdForUpdate(SUBJECT_ID)).thenReturn(Optional.of(subject));
    }

    private void mockUpdate(SubjectStatus expectedStatus) {
        when(subjectRepository.updateStatusWithReviewHistory(
                eq(SUBJECT_ID), eq(expectedStatus.getDbValue()), eq(7L), anyString()))
                .thenReturn(1);
    }

    private void mockRole(String roleKey) {
        when(userContextHolder.current()).thenReturn(new UserContext(
                999801L,
                UserContext.IdentityType.SYS_USER,
                USER_ID,
                10001L,
                101L,
                UserContext.DeptCategory.G,
                2,
                DataScopeType.ALL_COMMUNITY,
                AuthenticationLevel.L1,
                roleKey,
                Set.of(),
                Set.of()));
    }

    @Test
    public void submitForCommitteeReview_draftElection_persisted() {
        mockRole("GOV_OPERATOR");
        mockSubject(subject(SubjectStatus.DRAFT));
        mockUpdate(SubjectStatus.PENDING_COMMITTEE);

        VotingSubject result = service.submitForCommitteeReview(SUBJECT_ID, USER_ID);

        assertEquals(SubjectStatus.PENDING_COMMITTEE, result.getStatus());
        verify(subjectRepository).updateStatusWithReviewHistory(
                eq(SUBJECT_ID), eq(SubjectStatus.PENDING_COMMITTEE.getDbValue()), eq(7L), anyString());
    }

    @Test
    public void submitForCommitteeReview_nonDraftRejected() {
        mockRole("GOV_OPERATOR");
        mockSubject(subject(SubjectStatus.PUBLISHED));

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.submitForCommitteeReview(SUBJECT_ID, USER_ID));

        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_DRAFT, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    @Test
    public void committeeApprove_pendingCommittee_persisted() {
        mockRole("COMMUNITY_ADMIN");
        mockSubject(subject(SubjectStatus.PENDING_COMMITTEE));
        mockUpdate(SubjectStatus.PENDING_STREET);

        VotingSubject result = service.committeeApprove(SUBJECT_ID, USER_ID);

        assertEquals(SubjectStatus.PENDING_STREET, result.getStatus());
        ArgumentCaptor<String> history = ArgumentCaptor.forClass(String.class);
        verify(subjectRepository).updateStatusWithReviewHistory(
                eq(SUBJECT_ID), eq(SubjectStatus.PENDING_STREET.getDbValue()), eq(7L), history.capture());
        assertTrue(history.getValue().contains("\"action\":\"committeeApprove\""));
        assertTrue(history.getValue().contains("\"decision\":\"APPROVE\""));
        assertTrue(history.getValue().contains("\"reviewerUserId\":800001"));
        assertTrue(history.getValue().contains("\"fromStatus\":\"PENDING_COMMITTEE\""));
        assertTrue(history.getValue().contains("\"toStatus\":\"PENDING_STREET\""));
    }

    @Test
    public void committeeApprove_wrongStatusRejected() {
        mockRole("COMMUNITY_ADMIN");
        mockSubject(subject(SubjectStatus.DRAFT));

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.committeeApprove(SUBJECT_ID, USER_ID));

        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_PENDING_COMMITTEE, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    @Test
    public void committeeReject_requiresReason() {
        mockRole("COMMUNITY_ADMIN");
        mockSubject(subject(SubjectStatus.PENDING_COMMITTEE));

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.committeeReject(SUBJECT_ID, USER_ID, " "));

        assertEquals(VotingApplicationException.Reason.REVIEW_REJECT_REASON_REQUIRED, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    @Test
    public void committeeReject_pendingCommittee_backToDraft() {
        mockRole("COMMUNITY_ADMIN");
        mockSubject(subject(SubjectStatus.PENDING_COMMITTEE));
        mockUpdate(SubjectStatus.DRAFT);

        VotingSubject result = service.committeeReject(SUBJECT_ID, USER_ID, "材料不完整");

        assertEquals(SubjectStatus.DRAFT, result.getStatus());
    }

    @Test
    public void streetApprove_requiresApprovedCandidate() {
        mockRole("GOV_SUPER_ADMIN");
        mockSubject(subject(SubjectStatus.PENDING_STREET));
        when(electionCandidateRegistry.countActivePool(SUBJECT_ID))
                .thenReturn(new CandidatePoolSnapshot(0L, 0L));

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.streetApprove(SUBJECT_ID, USER_ID));

        assertEquals(VotingApplicationException.Reason.ELECTION_NO_APPROVED_CANDIDATE, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    @Test
    public void streetApprove_pendingStreetWithCandidate_published() {
        mockRole("GOV_SUPER_ADMIN");
        mockSubject(subject(SubjectStatus.PENDING_STREET));
        when(electionCandidateRegistry.countActivePool(SUBJECT_ID))
                .thenReturn(new CandidatePoolSnapshot(1L, 1L));
        mockUpdate(SubjectStatus.PUBLISHED);

        VotingSubject result = service.streetApprove(SUBJECT_ID, USER_ID);

        assertEquals(SubjectStatus.PUBLISHED, result.getStatus());
    }

    @Test
    public void streetReject_pendingStreet_backToDraft() {
        mockRole("GOV_SUPER_ADMIN");
        mockSubject(subject(SubjectStatus.PENDING_STREET));
        mockUpdate(SubjectStatus.DRAFT);

        VotingSubject result = service.streetReject(SUBJECT_ID, USER_ID, "街道办终审驳回");

        assertEquals(SubjectStatus.DRAFT, result.getStatus());
    }

    @Test
    public void nonElectionRejected() {
        mockRole("GOV_OPERATOR");
        VotingSubject general = subject(SubjectStatus.DRAFT);
        general.setSubjectType(SubjectType.GENERAL);
        mockSubject(general);

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.submitForCommitteeReview(SUBJECT_ID, USER_ID));

        assertEquals(VotingApplicationException.Reason.SUBJECT_TYPE_NOT_SUPPORTED, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    @Test
    public void streetAdminCannotPerformCommitteeReviewEvenIfPermissionMisconfigured() {
        mockRole("GOV_SUPER_ADMIN");

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.committeeApprove(SUBJECT_ID, USER_ID));

        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    public void communityAdminCannotPerformStreetReviewEvenIfPermissionMisconfigured() {
        mockRole("COMMUNITY_ADMIN");

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.streetApprove(SUBJECT_ID, USER_ID));

        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).findByIdForUpdate(anyLong());
    }
}
