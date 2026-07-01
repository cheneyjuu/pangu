package com.pangu.bootstrap.voting;

import com.pangu.application.handover.HandoverCircuitBreaker;
import com.pangu.application.voting.ProposalLifecycleService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingMobilizationService;
import com.pangu.application.voting.command.CancelSubjectCommand;
import com.pangu.application.voting.command.ProposeSubjectCommand;
import com.pangu.application.voting.command.PublishSubjectCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.Denominator;
import com.pangu.domain.model.voting.VotingDenominatorResolver;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Set;

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
 *   <li>公示：not-found / 非 DRAFT / 乐观锁失败 / GENERAL happy / ELECTION 必须走街道终审拒绝；</li>
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

    @Mock
    private HandoverCircuitBreaker handoverCircuitBreaker;

    @Mock
    private VotingDenominatorResolver denominatorResolver;

    @Mock
    private UserContextHolder userContextHolder;

    @Mock
    private VotingMobilizationService votingMobilizationService;

    @InjectMocks
    private ProposalLifecycleService service;

    private static final Long TENANT = 10001L;
    private static final Long PROPOSER = 800101L;
    private static final Long GOV = 800001L;
    private static final Long ACCT = 999001L;
    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-15T00:00:00Z");

    /**
     * 默认 mock 当前调用者为 GOV_OPERATOR（ELECTION 立项白名单），让所有 ELECTION 正向路径
     * 不被 M5 加的角色护栏拦截；个别测试覆盖具体角色时显式 stub。
     */
    @BeforeEach
    void mockCallerAsGovOperator() {
        when(userContextHolder.current()).thenReturn(ctxWithRole("GOV_OPERATOR"));
        when(denominatorResolver.resolve(any())).thenReturn(
                new Denominator(new BigDecimal("100.00"), 1L,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        9001L));
    }

    private static UserContext ctxWithRole(String roleKey) {
        return ctxWithRole(roleKey, 2);
    }

    private static UserContext ctxWithRole(String roleKey, Integer deptType) {
        return new UserContext(
                ACCT,
                UserContext.IdentityType.SYS_USER,
                GOV,
                TENANT,
                101L,
                UserContext.DeptCategory.G,
                deptType,
                DataScopeType.ALL_COMMUNITY,
                AuthenticationLevel.L1,
                roleKey,
                Set.of(),
                Set.of());
    }

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
        verify(denominatorResolver).resolve(persisted);
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
        verify(denominatorResolver, never()).resolve(any());
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

    @Test
    public void publish_electionDirectPublish_rejected() {
        VotingSubject s = draft();
        s.setSubjectType(SubjectType.ELECTION);
        s.setMaxWinners(3);
        s.setStatus(SubjectStatus.PENDING_STREET);
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.publish(new PublishSubjectCommand(7001L, GOV)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    @Test
    public void publish_electionDirectPublishRejected_evenForStreetAdmin() {
        when(userContextHolder.current()).thenReturn(ctxWithRole("GOV_SUPER_ADMIN"));
        VotingSubject s = draft();
        s.setSubjectType(SubjectType.ELECTION);
        s.setMaxWinners(3);
        s.setStatus(SubjectStatus.PENDING_STREET);
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.publish(new PublishSubjectCommand(7001L, GOV)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    // ===== M5 梯度 A：选举议题角色护栏 =====

    @Test
    public void propose_electionByCommitteeDirector_rejected() {
        when(userContextHolder.current()).thenReturn(ctxWithRole("COMMITTEE_DIRECTOR"));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(proposeCmd(SubjectType.ELECTION, START, END, 3)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).insert(any());
    }

    @Test
    public void propose_electionByPartySecretary_rejected() {
        // 党组书记仅做党组前置审查，不应立项选举（设计稿《选举闭环.md》§二·阶段一）
        when(userContextHolder.current()).thenReturn(ctxWithRole("PARTY_SECRETARY"));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(proposeCmd(SubjectType.ELECTION, START, END, 3)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).insert(any());
    }

    @Test
    public void propose_electionByCommunityAdmin_rejected() {
        // 源文件要求 ELECTION 新建由 G 端基层经办员实际执行，居委会管理员只进入后续初审。
        when(userContextHolder.current()).thenReturn(ctxWithRole("COMMUNITY_ADMIN"));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(proposeCmd(SubjectType.ELECTION, START, END, 3)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).insert(any());
    }

    @Test
    public void propose_electionByStreetAdmin_rejected() {
        // 街道办保留终审 / 公示权，不作为 ELECTION 新建立项执行人。
        when(userContextHolder.current()).thenReturn(ctxWithRole("GOV_SUPER_ADMIN"));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(proposeCmd(SubjectType.ELECTION, START, END, 3)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).insert(any());
    }

    @Test
    public void propose_electionByGovOperator_persisted() {
        when(userContextHolder.current()).thenReturn(ctxWithRole("GOV_OPERATOR"));
        when(subjectRepository.insert(any())).thenAnswer(inv -> {
            VotingSubject persisted = inv.getArgument(0);
            persisted.setSubjectId(7003L);
            return persisted;
        });
        VotingSubject result = service.propose(proposeCmd(SubjectType.ELECTION, START, END, 3));
        assertEquals(SubjectType.ELECTION, result.getSubjectType());
        assertEquals(SubjectStatus.DRAFT, result.getStatus());
        verify(subjectRepository).insert(any());
    }

    @Test
    public void propose_electionByGovOperatorOnPartyDept_rejected() {
        // 源文件要求基层经办员隶属居委会 / 网格组织（dept_type IN (2,5)），不能只按 G 端粗放行。
        when(userContextHolder.current()).thenReturn(ctxWithRole("GOV_OPERATOR", 6));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.propose(proposeCmd(SubjectType.ELECTION, START, END, 3)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).insert(any());
    }

    @Test
    public void publish_electionByCommunityAdmin_rejected() {
        // 居委会管理员有 voting:subject:publish，但 ELECTION 只能走街道终审，不走直接公示。
        when(userContextHolder.current()).thenReturn(ctxWithRole("COMMUNITY_ADMIN"));
        VotingSubject s = draft();
        s.setSubjectType(SubjectType.ELECTION);
        s.setMaxWinners(3);
        s.setStatus(SubjectStatus.PENDING_STREET);
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.publish(new PublishSubjectCommand(7001L, GOV)));
        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(subjectRepository, never()).updateStatus(anyLong(), anyInt(), anyLong());
    }

    @Test
    public void publish_generalByCommitteeDirector_persisted() {
        // 一般决议（GENERAL）公示无 ELECTION 护栏，业委会主任仍可正常公示
        when(userContextHolder.current()).thenReturn(ctxWithRole("COMMITTEE_DIRECTOR"));
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(draft()));
        when(subjectRepository.updateStatus(eq(7001L), eq(SubjectStatus.PUBLISHED.getDbValue()), anyLong()))
                .thenReturn(1);
        VotingSubject result = service.publish(new PublishSubjectCommand(7001L, GOV));
        assertEquals(SubjectStatus.PUBLISHED, result.getStatus());
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
        verify(votingMobilizationService).deactivateForSubject(eq(7001L), any());
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
        verify(votingMobilizationService, never()).activateForVotingOpened(any(), any());
    }

    @Test
    public void openVoting_publishedActivatesMobilizationPermissions() {
        VotingSubject s = draft();
        s.setStatus(SubjectStatus.PUBLISHED);
        when(subjectRepository.findByIdForUpdate(7001L)).thenReturn(Optional.of(s));
        when(subjectRepository.updateStatus(eq(7001L), eq(SubjectStatus.VOTING.getDbValue()), anyLong()))
                .thenReturn(1);

        VotingSubject result = service.openVoting(7001L, START.plusSeconds(60));

        assertEquals(SubjectStatus.VOTING, result.getStatus());
        verify(subjectRepository).updateStatus(eq(7001L), eq(SubjectStatus.VOTING.getDbValue()), anyLong());
        verify(votingMobilizationService).activateForVotingOpened(eq(result), eq(START.plusSeconds(60)));
    }
}
