package com.pangu.bootstrap.voting;

import com.pangu.application.voting.ElectionCandidateService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.command.NominateCandidateCommand;
import com.pangu.application.voting.command.ReviewCandidateCommand;
import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidateStatus;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ElectionCandidateService} 提名 / 资格审查 编排单元测试（Mockito）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>提名：议题不存在 / 非 ELECTION / 非 DRAFT&PUBLISHED / 跨租户 / 重复 → ALREADY_NOMINATED / 正向；</li>
 *   <li>审查：候选人不存在 / 终态再审冲突 / 乐观更新 0 行冲突 / 正向 approve。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ElectionCandidateServiceTest {

    @Mock
    private VotingSubjectRepository subjectRepository;
    @Mock
    private ElectionCandidateRegistry electionCandidateRegistry;

    @InjectMocks
    private ElectionCandidateService service;

    private static final Long SUBJECT_ID = 7001L;
    private static final Long TENANT = 10001L;
    private static final Long UID = 70001L;
    private static final Long OPERATOR = 800101L;

    private VotingSubject electionSubject(SubjectStatus status) {
        return VotingSubject.builder()
                .subjectId(SUBJECT_ID)
                .tenantId(TENANT)
                .title("业委会换届选举")
                .subjectType(SubjectType.ELECTION)
                .scope(VotingScope.COMMUNITY)
                .status(status)
                .maxWinners(3)
                .voteStartAt(Instant.parse("2026-07-01T00:00:00Z"))
                .voteEndAt(Instant.parse("2026-07-15T00:00:00Z"))
                .version(0L)
                .build();
    }

    private NominateCandidateCommand nominateCmd() {
        return new NominateCandidateCommand(SUBJECT_ID, UID, "张三", true, TENANT, OPERATOR);
    }

    // ===== nominate =====

    @Test
    public void nominate_subjectNotFound() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.empty());
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.nominate(nominateCmd()));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_FOUND, ex.getReason());
        verify(electionCandidateRegistry, never()).nominate(anyLong(), anyLong(), anyString(), anyBoolean());
    }

    @Test
    public void nominate_nonElectionRejected() {
        VotingSubject s = electionSubject(SubjectStatus.DRAFT);
        s.setSubjectType(SubjectType.GENERAL);
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.nominate(nominateCmd()));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_NOMINATABLE, ex.getReason());
    }

    @Test
    public void nominate_votingStatusRejected() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionSubject(SubjectStatus.VOTING)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.nominate(nominateCmd()));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_NOMINATABLE, ex.getReason());
    }

    @Test
    public void nominate_tenantMismatchRejected() {
        VotingSubject s = electionSubject(SubjectStatus.DRAFT);
        s.setTenantId(88888L);
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.nominate(nominateCmd()));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_NOMINATABLE, ex.getReason());
    }

    @Test
    public void nominate_duplicateTranslatedToAlreadyNominated() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionSubject(SubjectStatus.PUBLISHED)));
        when(electionCandidateRegistry.nominate(eq(SUBJECT_ID), eq(UID), anyString(), anyBoolean()))
                .thenThrow(new ElectionCandidateRegistry.DuplicateCandidateException("dup", new RuntimeException()));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.nominate(nominateCmd()));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_ALREADY_NOMINATED, ex.getReason());
    }

    @Test
    public void nominate_happyPath() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionSubject(SubjectStatus.DRAFT)));
        when(electionCandidateRegistry.nominate(eq(SUBJECT_ID), eq(UID), anyString(), anyBoolean()))
                .thenReturn(555L);
        Long candidateId = service.nominate(nominateCmd());
        assertEquals(555L, candidateId);
        verify(electionCandidateRegistry).nominate(eq(SUBJECT_ID), eq(UID), eq("张三"), eq(true));
    }

    // ===== review =====

    private Candidate pendingCandidate() {
        return Candidate.builder()
                .candidateId(555L)
                .subjectId(SUBJECT_ID)
                .uid(UID)
                .name("张三")
                .partyMember(true)
                .qualificationStatus(CandidateStatus.PENDING_REVIEW)
                .build();
    }

    @Test
    public void review_candidateNotFound() {
        when(electionCandidateRegistry.findById(555L)).thenReturn(Optional.empty());
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.review(new ReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_NOT_FOUND, ex.getReason());
    }

    @Test
    public void review_terminalStateConflict() {
        Candidate c = pendingCandidate();
        c.setQualificationStatus(CandidateStatus.APPROVED);
        when(electionCandidateRegistry.findById(555L)).thenReturn(Optional.of(c));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.review(new ReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT, ex.getReason());
        verify(electionCandidateRegistry, never()).updateQualification(anyLong(), anyInt());
    }

    @Test
    public void review_optimisticUpdateZeroRows_conflict() {
        when(electionCandidateRegistry.findById(555L)).thenReturn(Optional.of(pendingCandidate()));
        when(electionCandidateRegistry.updateQualification(eq(555L), eq(CandidateStatus.APPROVED.getDbValue())))
                .thenReturn(0);
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.review(new ReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT, ex.getReason());
    }

    @Test
    public void review_approveHappyPath() {
        when(electionCandidateRegistry.findById(555L)).thenReturn(Optional.of(pendingCandidate()));
        when(electionCandidateRegistry.updateQualification(eq(555L), eq(CandidateStatus.APPROVED.getDbValue())))
                .thenReturn(1);
        Candidate result = service.review(new ReviewCandidateCommand(555L, true, OPERATOR));
        assertEquals(CandidateStatus.APPROVED, result.getQualificationStatus());
        verify(electionCandidateRegistry).updateQualification(eq(555L), eq(CandidateStatus.APPROVED.getDbValue()));
    }

    @Test
    public void review_rejectHappyPath() {
        when(electionCandidateRegistry.findById(555L)).thenReturn(Optional.of(pendingCandidate()));
        when(electionCandidateRegistry.updateQualification(eq(555L), eq(CandidateStatus.REJECTED.getDbValue())))
                .thenReturn(1);
        Candidate result = service.review(new ReviewCandidateCommand(555L, false, OPERATOR));
        assertEquals(CandidateStatus.REJECTED, result.getQualificationStatus());
    }
}
