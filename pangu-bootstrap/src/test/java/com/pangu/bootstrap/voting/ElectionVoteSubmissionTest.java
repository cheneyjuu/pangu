package com.pangu.bootstrap.voting;

import com.pangu.application.voting.VoteSubmissionService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.command.CastVoteCommand;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.voting.Candidate;
import com.pangu.domain.model.voting.CandidateStatus;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.VoteItemRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VoteSubmissionService#cast} ELECTION 选举投票闸门单元测试（Mockito）。
 *
 * <p>覆盖 ELECTION 专属链路（GENERAL/MAJOR 主链路见 {@code VoteSubmissionServiceTest}）：
 * <ul>
 *   <li>缺 targetId → ELECTION_TARGET_REQUIRED；</li>
 *   <li>候选人不存在 / 不属本议题 / 非 APPROVED / 非 SUPPORT → CANDIDATE_NOT_VOTABLE；</li>
 *   <li>已投满 maxWinners → VOTE_LIMIT_EXCEEDED；</li>
 *   <li>正向：候选人 APPROVED + 未投满 → 返回 voteId。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ElectionVoteSubmissionTest {

    @Mock
    private VotingSubjectRepository subjectRepository;
    @Mock
    private VoteItemRepository voteItemRepository;
    @Mock
    private OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    @Mock
    private AbacPolicyEngine abacPolicyEngine;
    @Mock
    private UserContextHolder userContextHolder;
    @Mock
    private ElectionCandidateRegistry electionCandidateRegistry;

    @InjectMocks
    private VoteSubmissionService service;

    private static final Long SUBJECT_ID = 7001L;
    private static final Long UID = 70002L;
    private static final Long TENANT = 10001L;
    private static final Long OPID = 30002502L;
    private static final Long BUILDING = 30002L;
    private static final Long CANDIDATE_ID = 555L;
    private static final int MAX_WINNERS = 2;

    private CastVoteCommand cmd(Long targetId, VoteChoice choice) {
        return new CastVoteCommand(SUBJECT_ID, UID, TENANT, OPID, targetId, choice, null);
    }

    private VotingSubject electionVoting() {
        return VotingSubject.builder()
                .subjectId(SUBJECT_ID)
                .tenantId(TENANT)
                .title("业委会换届选举")
                .subjectType(SubjectType.ELECTION)
                .scope(VotingScope.COMMUNITY)
                .status(SubjectStatus.VOTING)
                .maxWinners(MAX_WINNERS)
                .voteStartAt(Instant.parse("2026-07-01T00:00:00Z"))
                .voteEndAt(Instant.parse("2026-07-15T00:00:00Z"))
                .version(0L)
                .build();
    }

    private Candidate candidate(Long subjectId, CandidateStatus status) {
        return Candidate.builder()
                .candidateId(CANDIDATE_ID)
                .subjectId(subjectId)
                .uid(70001L)
                .name("张三")
                .partyMember(true)
                .qualificationStatus(status)
                .build();
    }

    private OwnerPropertyVotingView validView() {
        return new OwnerPropertyVotingView(OPID, UID, TENANT, BUILDING,
                new BigDecimal("85.00"), true, 1);
    }

    @Test
    public void missingTargetRejected() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionVoting()));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd(null, VoteChoice.SUPPORT)));
        assertEquals(VotingApplicationException.Reason.ELECTION_TARGET_REQUIRED, ex.getReason());
    }

    @Test
    public void candidateNotFound_notVotable() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionVoting()));
        when(electionCandidateRegistry.findById(CANDIDATE_ID)).thenReturn(Optional.empty());
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd(CANDIDATE_ID, VoteChoice.SUPPORT)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_NOT_VOTABLE, ex.getReason());
    }

    @Test
    public void candidateNotApproved_notVotable() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionVoting()));
        when(electionCandidateRegistry.findById(CANDIDATE_ID))
                .thenReturn(Optional.of(candidate(SUBJECT_ID, CandidateStatus.PENDING_REVIEW)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd(CANDIDATE_ID, VoteChoice.SUPPORT)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_NOT_VOTABLE, ex.getReason());
    }

    @Test
    public void candidateOtherSubject_notVotable() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionVoting()));
        when(electionCandidateRegistry.findById(CANDIDATE_ID))
                .thenReturn(Optional.of(candidate(9999L, CandidateStatus.APPROVED)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd(CANDIDATE_ID, VoteChoice.SUPPORT)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_NOT_VOTABLE, ex.getReason());
    }

    @Test
    public void nonSupportChoice_notVotable() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionVoting()));
        when(electionCandidateRegistry.findById(CANDIDATE_ID))
                .thenReturn(Optional.of(candidate(SUBJECT_ID, CandidateStatus.APPROVED)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd(CANDIDATE_ID, VoteChoice.AGAINST)));
        assertEquals(VotingApplicationException.Reason.CANDIDATE_NOT_VOTABLE, ex.getReason());
    }

    @Test
    public void voteLimitExceeded() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionVoting()));
        when(electionCandidateRegistry.findById(CANDIDATE_ID))
                .thenReturn(Optional.of(candidate(SUBJECT_ID, CandidateStatus.APPROVED)));
        when(ownerPropertyVotingRepository.findByOpid(OPID)).thenReturn(Optional.of(validView()));
        // 已投满 maxWinners(=2)
        when(electionCandidateRegistry.countSupportByOpid(SUBJECT_ID, OPID)).thenReturn((long) MAX_WINNERS);
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd(CANDIDATE_ID, VoteChoice.SUPPORT)));
        assertEquals(VotingApplicationException.Reason.VOTE_LIMIT_EXCEEDED, ex.getReason());
        verify(voteItemRepository, never()).insert(anyLong(), any(), any());
    }

    @Test
    public void happyPath_underLimit_returnsVoteId() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(electionVoting()));
        when(electionCandidateRegistry.findById(CANDIDATE_ID))
                .thenReturn(Optional.of(candidate(SUBJECT_ID, CandidateStatus.APPROVED)));
        when(ownerPropertyVotingRepository.findByOpid(OPID)).thenReturn(Optional.of(validView()));
        when(electionCandidateRegistry.countSupportByOpid(SUBJECT_ID, OPID)).thenReturn(1L);
        when(voteItemRepository.insert(eq(SUBJECT_ID), any(), any())).thenReturn(777L);
        long voteId = service.cast(cmd(CANDIDATE_ID, VoteChoice.SUPPORT));
        assertEquals(777L, voteId);
        verify(voteItemRepository).insert(eq(SUBJECT_ID), any(), any());
    }
}
