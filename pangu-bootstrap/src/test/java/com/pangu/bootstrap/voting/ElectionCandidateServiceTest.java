package com.pangu.bootstrap.voting;

import com.pangu.application.voting.ElectionCandidateService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.command.NominateCandidateCommand;
import com.pangu.application.voting.command.PartyReviewCandidateCommand;
import com.pangu.application.voting.command.ReviewCandidateCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidateStatus;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ElectionCandidateService} 提名 / 两段资格审查 编排单元测试（Mockito）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>提名：议题不存在 / 非 ELECTION / 非 DRAFT&PUBLISHED / 跨租户 / 重复 → ALREADY_NOMINATED / 正向；</li>
 *   <li>党组前置审查 partyReview：PENDING_PARTY_REVIEW → PENDING_COMMITTEE_REVIEW / REJECTED；
 *       对已过前置审查的候选人再前置审查 → 冲突；</li>
 *   <li>居委会资格审查 review：候选人不存在 / 终态再审冲突 / 乐观更新 0 行冲突 /
 *       正向 approve(5→2) / reject(5→3) / 跳过党组前置审查直接资格通过 → 冲突。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ElectionCandidateServiceTest {

    @Mock
    private VotingSubjectRepository subjectRepository;
    @Mock
    private ElectionCandidateRegistry electionCandidateRegistry;
    @Mock
    private UserContextHolder userContextHolder;

    @InjectMocks
    private ElectionCandidateService service;

    private static final Long SUBJECT_ID = 7001L;
    private static final Long TENANT = 10001L;
    private static final Long UID = 70001L;
    private static final Long OPERATOR = 800101L;

    private static final int PARTY = CandidateStatus.PENDING_PARTY_REVIEW.getDbValue();      // 1
    private static final int COMMITTEE = CandidateStatus.PENDING_COMMITTEE_REVIEW.getDbValue(); // 5
    private static final int APPROVED = CandidateStatus.APPROVED.getDbValue();               // 2
    private static final int REJECTED = CandidateStatus.REJECTED.getDbValue();               // 3
    private static final String REJECT_EVIDENCE = "{\"rule\":\"insufficientMaterial\",\"files\":[\"oss://evidence/1.pdf\"]}";

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

    @BeforeEach
    public void setUp() {
        when(userContextHolder.current()).thenReturn(ctx("GOV_OPERATOR", 2));
    }

    private UserContext ctx(String roleKey, Integer deptType) {
        return new UserContext(
                999805L,
                UserContext.IdentityType.SYS_USER,
                OPERATOR,
                TENANT,
                101L,
                UserContext.DeptCategory.G,
                deptType,
                DataScopeType.ALL_COMMUNITY,
                AuthenticationLevel.L1,
                roleKey,
                java.util.Set.of("candidate:nominate"),
                java.util.Set.of());
    }

    private void mockRole(String roleKey) {
        when(userContextHolder.current()).thenReturn(ctx(roleKey, "COMMUNITY_ADMIN".equals(roleKey) ? 2 : 5));
    }

    private Candidate candidate(CandidateStatus status) {
        return Candidate.builder()
                .candidateId(555L)
                .subjectId(SUBJECT_ID)
                .uid(UID)
                .name("张三")
                .partyMember(true)
                .qualificationStatus(status)
                .build();
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
    public void nominate_gridOperatorRejectedEvenWithLegacyPermission() {
        when(userContextHolder.current()).thenReturn(ctx("GRID_MEMBER", 5));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionSubject(SubjectStatus.DRAFT)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.nominate(nominateCmd()));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(electionCandidateRegistry, never()).nominate(anyLong(), anyLong(), anyString(), anyBoolean());
    }

    @Test
    public void nominate_committeeDirectorRejectedEvenWithLegacyPermission() {
        when(userContextHolder.current()).thenReturn(ctx("COMMITTEE_DIRECTOR", 10));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionSubject(SubjectStatus.DRAFT)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.nominate(nominateCmd()));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(electionCandidateRegistry, never()).nominate(anyLong(), anyLong(), anyString(), anyBoolean());
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

    // ===== partyReview（党组书记前置审查：1 → 5 / 3）=====

    @Test
    public void partyReview_approve_pendingPartyToCommittee() {
        mockRole("PARTY_SECRETARY");
        when(electionCandidateRegistry.findById(555L))
                .thenReturn(Optional.of(candidate(CandidateStatus.PENDING_PARTY_REVIEW)));
        when(electionCandidateRegistry.updateQualification(
                eq(555L), eq(PARTY), eq(COMMITTEE), any(), any(), any(), any())).thenReturn(1);
        Candidate result = service.partyReview(new PartyReviewCandidateCommand(555L, true, OPERATOR));
        assertEquals(CandidateStatus.PENDING_COMMITTEE_REVIEW, result.getQualificationStatus());
        verify(electionCandidateRegistry).updateQualification(
                eq(555L), eq(PARTY), eq(COMMITTEE), any(), any(), any(), any());
    }

    @Test
    public void partyReview_reject_pendingPartyToRejected() {
        mockRole("PARTY_SECRETARY");
        when(electionCandidateRegistry.findById(555L))
                .thenReturn(Optional.of(candidate(CandidateStatus.PENDING_PARTY_REVIEW)));
        when(electionCandidateRegistry.updateQualification(
                eq(555L), eq(PARTY), eq(REJECTED), eq("C1"), eq(REJECT_EVIDENCE),
                eq(OPERATOR), eq("PARTY_REVIEW"))).thenReturn(1);
        Candidate result = service.partyReview(new PartyReviewCandidateCommand(
                555L, false, OPERATOR, "C1", REJECT_EVIDENCE));
        assertEquals(CandidateStatus.REJECTED, result.getQualificationStatus());
        verify(electionCandidateRegistry).updateQualification(
                eq(555L), eq(PARTY), eq(REJECTED), eq("C1"), eq(REJECT_EVIDENCE),
                eq(OPERATOR), eq("PARTY_REVIEW"));
    }

    @Test
    public void partyReview_rejectWithoutReasonCode_rejected() {
        mockRole("PARTY_SECRETARY");
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.partyReview(new PartyReviewCandidateCommand(555L, false, OPERATOR, null, REJECT_EVIDENCE)));
        assertEquals(VotingApplicationException.Reason.REJECT_REASON_CODE_REQUIRED, ex.getReason());
        verify(electionCandidateRegistry, never()).findById(anyLong());
    }

    @Test
    public void partyReview_alreadyAdvancedToCommittee_conflict() {
        mockRole("PARTY_SECRETARY");
        when(electionCandidateRegistry.findById(555L))
                .thenReturn(Optional.of(candidate(CandidateStatus.PENDING_COMMITTEE_REVIEW)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.partyReview(new PartyReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT, ex.getReason());
        verify(electionCandidateRegistry, never()).updateQualification(
                anyLong(), anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    public void partyReview_candidateNotFound() {
        mockRole("PARTY_SECRETARY");
        when(electionCandidateRegistry.findById(555L)).thenReturn(Optional.empty());
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.partyReview(new PartyReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_NOT_FOUND, ex.getReason());
    }

    // ===== review（居委会资格审查：5 → 2 / 3）=====

    @Test
    public void review_candidateNotFound() {
        mockRole("COMMUNITY_ADMIN");
        when(electionCandidateRegistry.findById(555L)).thenReturn(Optional.empty());
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.review(new ReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_NOT_FOUND, ex.getReason());
    }

    @Test
    public void review_terminalStateConflict() {
        mockRole("COMMUNITY_ADMIN");
        when(electionCandidateRegistry.findById(555L))
                .thenReturn(Optional.of(candidate(CandidateStatus.APPROVED)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.review(new ReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT, ex.getReason());
        verify(electionCandidateRegistry, never()).updateQualification(
                anyLong(), anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    public void review_skipPartyPreReview_conflict() {
        // 候选人仍在党组前置审查阶段（未过前置审查），直接居委会资格通过应被状态机拒绝。
        mockRole("COMMUNITY_ADMIN");
        when(electionCandidateRegistry.findById(555L))
                .thenReturn(Optional.of(candidate(CandidateStatus.PENDING_PARTY_REVIEW)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.review(new ReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT, ex.getReason());
        verify(electionCandidateRegistry, never()).updateQualification(
                anyLong(), anyInt(), anyInt(), any(), any(), any(), any());
    }

    @Test
    public void review_optimisticUpdateZeroRows_conflict() {
        mockRole("COMMUNITY_ADMIN");
        when(electionCandidateRegistry.findById(555L))
                .thenReturn(Optional.of(candidate(CandidateStatus.PENDING_COMMITTEE_REVIEW)));
        when(electionCandidateRegistry.updateQualification(
                eq(555L), eq(COMMITTEE), eq(APPROVED), any(), any(), any(), any())).thenReturn(0);
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.review(new ReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_REVIEW_CONFLICT, ex.getReason());
    }

    @Test
    public void review_approveHappyPath() {
        mockRole("COMMUNITY_ADMIN");
        when(electionCandidateRegistry.findById(555L))
                .thenReturn(Optional.of(candidate(CandidateStatus.PENDING_COMMITTEE_REVIEW)));
        when(electionCandidateRegistry.updateQualification(
                eq(555L), eq(COMMITTEE), eq(APPROVED), any(), any(), any(), any())).thenReturn(1);
        Candidate result = service.review(new ReviewCandidateCommand(555L, true, OPERATOR));
        assertEquals(CandidateStatus.APPROVED, result.getQualificationStatus());
        verify(electionCandidateRegistry).updateQualification(
                eq(555L), eq(COMMITTEE), eq(APPROVED), any(), any(), any(), any());
    }

    @Test
    public void review_rejectHappyPath() {
        mockRole("COMMUNITY_ADMIN");
        when(electionCandidateRegistry.findById(555L))
                .thenReturn(Optional.of(candidate(CandidateStatus.PENDING_COMMITTEE_REVIEW)));
        when(electionCandidateRegistry.updateQualification(
                eq(555L), eq(COMMITTEE), eq(REJECTED), eq("C2"), eq(REJECT_EVIDENCE),
                eq(OPERATOR), eq("COMMITTEE_REVIEW"))).thenReturn(1);
        Candidate result = service.review(new ReviewCandidateCommand(
                555L, false, OPERATOR, "C2", REJECT_EVIDENCE));
        assertEquals(CandidateStatus.REJECTED, result.getQualificationStatus());
    }

    @Test
    public void govSuperAdminCannotPartyReviewEvenIfPermissionMisconfigured() {
        mockRole("GOV_SUPER_ADMIN");
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.partyReview(new PartyReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_REVIEW_FORBIDDEN, ex.getReason());
        verify(electionCandidateRegistry, never()).findById(anyLong());
    }

    @Test
    public void govSuperAdminCannotCommitteeReviewEvenIfPermissionMisconfigured() {
        mockRole("GOV_SUPER_ADMIN");
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.review(new ReviewCandidateCommand(555L, true, OPERATOR)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_REVIEW_FORBIDDEN, ex.getReason());
        verify(electionCandidateRegistry, never()).findById(anyLong());
    }
}
