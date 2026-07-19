// 关联业务：验证普通线上、纸质及线下代录投票的资格、渠道和重复提交闸门。
package com.pangu.bootstrap.voting;

import com.pangu.application.voting.VoteSubmissionService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.VotingExecutionService;
import com.pangu.application.voting.command.CastVoteCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.gateway.VoteCastMonitorGateway;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.policy.AbacPolicyEngine;
import com.pangu.domain.policy.EvaluationResult;
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
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VoteSubmissionService#cast} 业主投票主链路单元测试（Mockito）。
 *
 * <p>覆盖 cast 流水线上每一道闸门：
 * <ul>
 *   <li>议题不存在 / 非 VOTING / ELECTION 缺 targetId / 租户不一致；</li>
 *   <li>MAJOR 议题 L3 face-auth 不足 → AUTH_LEVEL_INSUFFICIENT；</li>
 *   <li>opid 不存在 / 非本人 → OPID_NOT_OWNED；</li>
 *   <li>账户欠费冻结 / BUILDING scope 不匹配 → OPID_OUT_OF_SCOPE；</li>
 *   <li>UNIQUE 冲突 → VOTE_ALREADY_CAST；</li>
 *   <li>GENERAL 正向：不触发 ABAC，返回 voteId。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class VoteSubmissionServiceTest {

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
    @Mock
    private VoteCastMonitorGateway voteCastMonitorGateway;
    @Mock
    private VotingExecutionService votingExecutionService;

    @InjectMocks
    private VoteSubmissionService service;

    private static final Long SUBJECT_ID = 7001L;
    private static final Long UID = 70002L;
    private static final Long TENANT = 10001L;
    private static final Long OPID = 30002502L;
    private static final Long BUILDING = 30002L;

    private CastVoteCommand cmd() {
        return new CastVoteCommand(SUBJECT_ID, UID, TENANT, OPID, null, VoteChoice.SUPPORT, null);
    }

    private CastVoteCommand cmd(VoteChannel voteChannel) {
        return new CastVoteCommand(SUBJECT_ID, UID, TENANT, OPID, null, VoteChoice.SUPPORT, null, voteChannel);
    }

    private VotingSubject votingSubject(SubjectType type, VotingScope scope, Long scopeRef) {
        return VotingSubject.builder()
                .subjectId(SUBJECT_ID)
                .tenantId(TENANT)
                .title("议案")
                .subjectType(type)
                .scope(scope)
                .scopeReferenceId(scopeRef)
                .status(SubjectStatus.VOTING)
                .voteStartAt(Instant.parse("2026-07-01T00:00:00Z"))
                .voteEndAt(Instant.parse("2026-07-15T00:00:00Z"))
                .version(0L)
                .build();
    }

    private OwnerPropertyVotingView view(Long uid, Long tenantId, Long buildingId,
                                         boolean delegate, int accountStatus) {
        return new OwnerPropertyVotingView(OPID, uid, tenantId, buildingId,
                new BigDecimal("85.00"), delegate, accountStatus);
    }

    private UserContext ctx(AuthenticationLevel level) {
        return new UserContext(999913L, UserContext.IdentityType.C_USER, UID, TENANT,
                null, null, null, null, level, null, null, null);
    }

    // ===== 议题级闸门 =====

    @Test
    public void subjectNotFound() {
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.empty());
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_FOUND, ex.getReason());
    }

    @Test
    public void notVotingStatusRejected() {
        VotingSubject s = votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null);
        s.setStatus(SubjectStatus.PUBLISHED);
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.SUBJECT_NOT_VOTING_CASTABLE, ex.getReason());
    }

    @Test
    public void electionMissingTargetRejected() {
        // M3-3：ELECTION 已放开投票，但 cmd() 未带 targetId（候选人）→ ELECTION_TARGET_REQUIRED
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.ELECTION, VotingScope.COMMUNITY, null)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.ELECTION_TARGET_REQUIRED, ex.getReason());
    }

    @Test
    public void tenantMismatchOutOfScope() {
        VotingSubject s = votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null);
        s.setTenantId(88888L); // 与 cmd().tenantId 不一致
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(s));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.OPID_OUT_OF_SCOPE, ex.getReason());
    }

    // ===== MAJOR L3 闸门 =====

    @Test
    public void majorWithInsufficientAuthLevel() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.MAJOR, VotingScope.COMMUNITY, null)));
        when(userContextHolder.current()).thenReturn(ctx(AuthenticationLevel.L1));
        when(abacPolicyEngine.evaluateVoting(eq(UID), eq(TENANT), eq(AuthenticationLevel.L1)))
                .thenReturn(EvaluationResult.denied("需 L3 人脸实名", "L3_REQUIRED", "LIMIT_MAJOR_VOTE", true));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.AUTH_LEVEL_INSUFFICIENT, ex.getReason());
        verify(voteItemRepository, never()).insert(anyLong(), any(), any());
    }

    // ===== opid 归属 / scope 闸门 =====

    @Test
    public void opidNotFound() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null)));
        when(ownerPropertyVotingRepository.findByOpid(OPID)).thenReturn(Optional.empty());
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.OPID_NOT_OWNED, ex.getReason());
    }

    @Test
    public void opidNotOwnedByUid() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null)));
        // 该 opid 属于业主 70001，不是当前 70002
        when(ownerPropertyVotingRepository.findByOpid(OPID))
                .thenReturn(Optional.of(view(70001L, TENANT, BUILDING, true, 1)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.OPID_NOT_OWNED, ex.getReason());
    }

    @Test
    public void delegateOrAccountNotValidOutOfScope() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null)));
        // 非投票代表 → isValidForVoting=false
        when(ownerPropertyVotingRepository.findByOpid(OPID))
                .thenReturn(Optional.of(view(UID, TENANT, BUILDING, false, 1)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.OPID_OUT_OF_SCOPE, ex.getReason());
    }

    @Test
    public void buildingScopeMismatchOutOfScope() {
        // 议题 scope=BUILDING/30099，业主房产在 30002 → 不匹配
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.GENERAL, VotingScope.BUILDING, 30099L)));
        when(ownerPropertyVotingRepository.findByOpid(OPID))
                .thenReturn(Optional.of(view(UID, TENANT, BUILDING, true, 1)));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.OPID_OUT_OF_SCOPE, ex.getReason());
    }

    // ===== 重复投票 =====

    @Test
    public void duplicateVoteTranslatedToAlreadyCast() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null)));
        when(ownerPropertyVotingRepository.findByOpid(OPID))
                .thenReturn(Optional.of(view(UID, TENANT, BUILDING, true, 1)));
        when(voteItemRepository.insert(eq(SUBJECT_ID), any(), any()))
                .thenThrow(new VoteItemRepository.DuplicateVoteException("dup", new RuntimeException()));
        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.cast(cmd()));
        assertEquals(VotingApplicationException.Reason.VOTE_ALREADY_CAST, ex.getReason());
        verify(voteCastMonitorGateway, never()).recordCast(any());
    }

    // ===== 正向 =====

    @Test
    public void generalHappyPath_noAbac_returnsVoteId() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null)));
        when(ownerPropertyVotingRepository.findByOpid(OPID))
                .thenReturn(Optional.of(view(UID, TENANT, BUILDING, true, 1)));
        when(voteItemRepository.insert(eq(SUBJECT_ID), any(), any())).thenReturn(555L);

        long voteId = service.cast(cmd());
        assertEquals(555L, voteId);
        // GENERAL 议题不触发 L3 face-auth
        verify(abacPolicyEngine, never()).evaluateVoting(anyLong(), anyLong(), any());
        verify(voteCastMonitorGateway).recordCast(any());
    }

    @Test
    public void missingVoteChannel_defaultsToOnlineNotUnsignedLikePaper() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null)));
        when(ownerPropertyVotingRepository.findByOpid(OPID))
                .thenReturn(Optional.of(view(UID, TENANT, BUILDING, true, 1)));
        when(voteItemRepository.insert(eq(SUBJECT_ID), any(), any())).thenReturn(556L);

        service.cast(cmd());

        org.mockito.ArgumentCaptor<com.pangu.domain.model.voting.VoteItem> itemCaptor =
                forClass(com.pangu.domain.model.voting.VoteItem.class);
        verify(voteItemRepository).insert(eq(SUBJECT_ID), itemCaptor.capture(), any());
        assertEquals(VoteChannel.ONLINE, itemCaptor.getValue().getVoteChannel());

        org.mockito.ArgumentCaptor<VoteCastMonitorGateway.VoteCastEvent> eventCaptor =
                forClass(VoteCastMonitorGateway.VoteCastEvent.class);
        verify(voteCastMonitorGateway).recordCast(eventCaptor.capture());
        assertEquals(VoteChannel.ONLINE, eventCaptor.getValue().voteChannel());
        assertEquals(false, eventCaptor.getValue().unsignedLikePaper());
    }

    @Test
    public void paperVoteChannel_recordsPaperLikeMonitorEvent() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.GENERAL, VotingScope.COMMUNITY, null)));
        when(ownerPropertyVotingRepository.findByOpid(OPID))
                .thenReturn(Optional.of(view(UID, TENANT, BUILDING, true, 1)));
        when(voteItemRepository.insert(eq(SUBJECT_ID), any(), any())).thenReturn(557L);

        service.cast(cmd(VoteChannel.PAPER));

        org.mockito.ArgumentCaptor<VoteCastMonitorGateway.VoteCastEvent> eventCaptor =
                forClass(VoteCastMonitorGateway.VoteCastEvent.class);
        verify(voteCastMonitorGateway).recordCast(eventCaptor.capture());
        assertEquals(VoteChannel.PAPER, eventCaptor.getValue().voteChannel());
        assertEquals(true, eventCaptor.getValue().unsignedLikePaper());
    }

    @Test
    public void majorOfflineProxyVote_skipsOnlineL3GateAndRecordsPaperLike() {
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Optional.of(votingSubject(SubjectType.MAJOR, VotingScope.COMMUNITY, null)));
        when(ownerPropertyVotingRepository.findByOpid(OPID))
                .thenReturn(Optional.of(view(UID, TENANT, BUILDING, true, 1)));
        when(voteItemRepository.insert(eq(SUBJECT_ID), any(), any())).thenReturn(558L);

        long voteId = service.cast(cmd(VoteChannel.OFFLINE_PROXY));

        assertEquals(558L, voteId);
        verify(abacPolicyEngine, never()).evaluateVoting(anyLong(), anyLong(), any());
        org.mockito.ArgumentCaptor<VoteCastMonitorGateway.VoteCastEvent> eventCaptor =
                forClass(VoteCastMonitorGateway.VoteCastEvent.class);
        verify(voteCastMonitorGateway).recordCast(eventCaptor.capture());
        assertEquals(VoteChannel.OFFLINE_PROXY, eventCaptor.getValue().voteChannel());
        assertEquals(true, eventCaptor.getValue().unsignedLikePaper());
    }
}
