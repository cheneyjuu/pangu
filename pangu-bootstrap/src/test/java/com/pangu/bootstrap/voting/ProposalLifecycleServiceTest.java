package com.pangu.bootstrap.voting;

import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.command.CancelSubjectCommand;
import com.pangu.application.voting.command.ProposeSubjectCommand;
import com.pangu.application.voting.command.PublishSubjectCommand;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProposalLifecycleService} 立项 / 公示 / 撤回 / 开票 编排单元测试（Mockito，
 * 与 {@code PartyRatioCircuitBreakerTest} 同风格，纯 stub 不起 Spring）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>立项：ELECTION 缺 maxWinners 拒 / ELECTION 带 maxWinners 正向 / 非法参数翻 PROPOSE_FORBIDDEN_FOR_TYPE / GENERAL 正向落库；</li>
 *   <li>公示：not-found / 非 DRAFT / 乐观锁失败；</li>
 *   <li>撤回：VOTING+ 一律拒 / DRAFT 非本人拒 / PUBLISHED 政府强撤正向；</li>
 *   <li>开票：非 PUBLISHED 拒。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProposalLifecycleServiceTest {

    @Mock
    private VotingSubjectRepository subjectRepository;

    @Mock
    private OwnerPropertyVotingRepository ownerPropertyVotingRepository;

    @InjectMocks
    private ProposalLifecycleService service;

    private static final Long TENANT = 10001L;
    private static final Long PROPOSER = 800101L;
    private static final Long GOV = 800001L;
    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-15T00:00:00Z");

    private ProposeSubjectCommand proposeCmd(SubjectType type, Instant start, Instant end) {
        return proposeCmd(type, start, end, null);
    }

    private ProposeSubjectCommand proposeCmd(SubjectType type, Instant start, Instant end, Integer maxWinners) {
        return new ProposeSubjectCommand(TENANT, type, VotingScope.COMMUNITY, null,
                "维修资金动用议案", start, end, PROPOSER, new BigDecimal("0.50"), maxWinners);
    }

    private VotingSubject draft() {
        return VotingSubject.builder()
                .subjectId(7001L)
                .tenantId(TENANT)
                .title("维修资金动用议案")
                .subjectType(SubjectType.GENERAL)
                .scope(VotingScope.COMMUNITY)
                .status(SubjectStatus.DRAFT)
                .voteStartAt(START)
                .voteEndAt(END)
                .proposedByUserId(PROPOSER)
                .version(0L)
                .build();
    }

    // ===== propose =====

    @Test
    public void propose_electionWithoutMaxWinners_rejected() {
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(proposeCmd(SubjectType.ELECTION, START, END, null)));
        assertEquals(VotingApplicationException.Reason.ELECTION_MAX_WINNERS_REQUIRED, ex.getReason());
        verify(subjectRepository, never()).insert(any());
    }

    @Test
    public void propose_electionWithMaxWinners_persisted() {
        when(subjectRepository.insert(any())).thenAnswer(inv -> {
            VotingSubject s = inv.getArgument(0);
            s.setSubjectId(7002L);
            return s;
        });
        VotingSubject persisted = service.propose(proposeCmd(SubjectType.ELECTION, START, END, 3));
        assertEquals(SubjectStatus.DRAFT, persisted.getStatus());
        assertEquals(SubjectType.ELECTION, persisted.getSubjectType());
        assertEquals(3, persisted.getMaxWinners());
        verify(subjectRepository).insert(any());
    }

    @Test
    public void propose_generalPersisted() {
        when(subjectRepository.insert(any())).thenAnswer(inv -> {
            VotingSubject s = inv.getArgument(0);
            s.setSubjectId(7001L);
            return s;
        });
        VotingSubject persisted = service.propose(proposeCmd(SubjectType.GENERAL, START, END));
        assertEquals(SubjectStatus.DRAFT, persisted.getStatus());
        assertEquals(7001L, persisted.getSubjectId());
        verify(subjectRepository).insert(any());
    }

    @Test
    public void propose_invalidParams_translatedToProposeForbidden() {
        // voteEnd 早于 voteStart → VotingSubjectActions.open 抛 IllegalArgumentException
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(proposeCmd(SubjectType.GENERAL, END, START)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).insert(any());
    }

    // ===== publish =====

    @Test
    public void publish_notFound() {
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.empty());
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.publish(new PublishSubjectCommand(7001L, GOV)));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_FOUND, ex.getReason());
    }

    @Test
    public void publish_notDraftRejected() {
        VotingSubject s = draft();
        s.setStatus(SubjectStatus.VOTING);
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.publish(new PublishSubjectCommand(7001L, GOV)));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_DRAFT, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    @Test
    public void publish_optimisticLockFailure() {
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(draft()));
        when(subjectRepository.updateStatus(eq(7001L), eq(SubjectStatus.PUBLISHED.getDbValue()), anyLong()))
                .thenReturn(0);
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.publish(new PublishSubjectCommand(7001L, GOV)));
        assertEquals(VotingApplicationException.Reason.CONCURRENT_LIFECYCLE_MODIFICATION, ex.getReason());
    }

    @Test
    public void publish_happyPath() {
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(draft()));
        when(subjectRepository.updateStatus(eq(7001L), eq(SubjectStatus.PUBLISHED.getDbValue()), anyLong()))
                .thenReturn(1);
        VotingSubject result = service.publish(new PublishSubjectCommand(7001L, GOV));
        assertEquals(SubjectStatus.PUBLISHED, result.getStatus());
        verify(subjectRepository).updateStatus(eq(7001L), eq(SubjectStatus.PUBLISHED.getDbValue()), anyLong());
    }

    // ===== cancel =====

    @Test
    public void cancel_votingForbidden() {
        VotingSubject s = draft();
        s.setStatus(SubjectStatus.VOTING);
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cancel(new CancelSubjectCommand(7001L, GOV, "投票中强撤", true)));
        assertEquals(VotingApplicationException.Reason.CANCEL_FORBIDDEN, ex.getReason());
        verify(subjectRepository, never()).cancel(any(), anyLong());
    }

    @Test
    public void cancel_draftByNonProposer_forbidden() {
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(draft()));
        // byGovernment=false 走 cancelByProposer，currentUserId != proposedByUserId → IllegalSubjectTransition
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cancel(new CancelSubjectCommand(7001L, 999999L, "非本人撤", false)));
        assertEquals(VotingApplicationException.Reason.CANCEL_FORBIDDEN, ex.getReason());
        verify(subjectRepository, never()).cancel(any(), anyLong());
    }

    @Test
    public void cancel_governmentOnPublished_happyPath() {
        VotingSubject s = draft();
        s.setStatus(SubjectStatus.PUBLISHED);
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(s));
        when(subjectRepository.cancel(any(), anyLong())).thenReturn(1);
        VotingSubject result = service.cancel(new CancelSubjectCommand(7001L, GOV, "街道办强撤：内容违规", true));
        assertEquals(SubjectStatus.CANCELLED, result.getStatus());
        assertEquals(GOV, result.getCancelledByUserId());
        verify(subjectRepository).cancel(any(), anyLong());
    }

    // ===== openVoting =====

    @Test
    public void openVoting_notPublishedRejected() {
        VotingSubject s = draft(); // DRAFT，未公示
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.openVoting(7001L, START.plusSeconds(60)));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_PUBLISHED, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }
}
