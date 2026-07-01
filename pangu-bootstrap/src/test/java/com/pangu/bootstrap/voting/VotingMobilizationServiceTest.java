package com.pangu.bootstrap.voting;

import com.pangu.application.voting.VotingMobilizationService;
import com.pangu.application.voting.VotingApplicationException;
import com.pangu.application.voting.command.SendMobilizationReminderCommand;
import com.pangu.application.voting.VoteSubmissionService;
import com.pangu.application.voting.command.CastVoteCommand;
import com.pangu.application.voting.command.OfflineProxyVoteCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.user.AuthenticationLevel;
import com.pangu.domain.model.user.DataScopeType;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VoteChoice;
import com.pangu.domain.model.voting.VotingMobilizationPermission;
import com.pangu.domain.model.voting.VotingMobilizationReminder;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.VotingMobilizationReminderRepository;
import com.pangu.domain.repository.VotingMobilizationPermissionRepository;
import com.pangu.domain.repository.VotingReminderOutboxGateway;
import com.pangu.domain.repository.VotingSubjectRepository;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VotingMobilizationServiceTest {

    @Mock
    private VotingMobilizationPermissionRepository permissionRepository;
    @Mock
    private VotingMobilizationReminderRepository reminderRepository;
    @Mock
    private VotingReminderOutboxGateway reminderOutboxGateway;
    @Mock
    private VotingSubjectRepository subjectRepository;
    @Mock
    private OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    @Mock
    private VoteSubmissionService voteSubmissionService;
    @Mock
    private UserContextHolder userContextHolder;

    @InjectMocks
    private VotingMobilizationService service;

    private static final Long SUBJECT = 7001L;
    private static final Long TENANT = 10001L;
    private static final Long USER = 800102L;
    private static final Long OPID = 30001101L;

    private VotingSubject subject(SubjectStatus status) {
        return VotingSubject.builder()
                .subjectId(SUBJECT)
                .tenantId(TENANT)
                .title("动员权限测试")
                .subjectType(SubjectType.GENERAL)
                .scope(VotingScope.COMMUNITY)
                .status(status)
                .voteEndAt(Instant.parse("2026-07-15T00:00:00Z"))
                .version(0L)
                .build();
    }

    private UserContext sysUser() {
        return new UserContext(
                999812L,
                UserContext.IdentityType.SYS_USER,
                USER,
                TENANT,
                110L,
                UserContext.DeptCategory.B,
                11,
                DataScopeType.OWNER_GROUP,
                AuthenticationLevel.L1,
                "OWNER_REPRESENTATIVE",
                Set.of("voting:subject:audit"),
                Set.of(30001L));
    }

    @Test
    public void activateForVotingOpened_delegatesToRepositoryWithSubjectScope() {
        Instant openedAt = Instant.parse("2026-07-01T00:00:00Z");
        when(permissionRepository.activateForSubject(
                eq(SUBJECT), eq(TENANT), eq(VotingScope.COMMUNITY), eq(null), eq(openedAt), any()))
                .thenReturn(2);

        int count = service.activateForVotingOpened(subject(SubjectStatus.VOTING), openedAt);

        assertEquals(2, count);
    }

    @Test
    public void listMine_nonVotingSubject_returnsEmptyAndSkipsPermissionLookup() {
        when(userContextHolder.current()).thenReturn(sysUser());
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SubjectStatus.PUBLISHED)));

        List<VotingMobilizationPermission> permissions = service.listMine(SUBJECT);

        assertEquals(0, permissions.size());
        verify(permissionRepository, never()).findActiveBySubjectAndUser(any(), any(), any(), any());
    }

    @Test
    public void listMine_votingSubject_returnsActivePermissionsForCurrentSysUser() {
        when(userContextHolder.current()).thenReturn(sysUser());
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SubjectStatus.VOTING)));
        when(permissionRepository.findActiveBySubjectAndUser(eq(SUBJECT), eq(TENANT), eq(USER), any()))
                .thenReturn(List.of(VotingMobilizationPermission.builder()
                        .subjectId(SUBJECT)
                        .tenantId(TENANT)
                        .userId(USER)
                        .buildingId(30001L)
                        .roleKey("OWNER_REPRESENTATIVE")
                        .canRemind(true)
                        .canOfflineProxy(true)
                        .status(1)
                        .build()));

        List<VotingMobilizationPermission> permissions = service.listMine(SUBJECT);

        assertEquals(1, permissions.size());
        assertEquals(30001L, permissions.get(0).getBuildingId());
    }

    @Test
    public void sendReminder_withoutBuildingPermission_rejectedBeforeOutbox() {
        when(userContextHolder.current()).thenReturn(sysUser());
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SubjectStatus.VOTING)));
        when(permissionRepository.findActiveBySubjectAndUser(eq(SUBJECT), eq(TENANT), eq(USER), any()))
                .thenReturn(List.of());

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.sendReminder(new SendMobilizationReminderCommand(
                        SUBJECT, 30001L, "请尽快完成投票")));

        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(reminderOutboxGateway, never()).enqueueReminderRequested(any());
        verify(reminderRepository, never()).insert(any());
    }

    @Test
    public void sendReminder_withActivePermission_recordsReminderAndOutbox() {
        when(userContextHolder.current()).thenReturn(sysUser());
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SubjectStatus.VOTING)));
        when(permissionRepository.findActiveBySubjectAndUser(eq(SUBJECT), eq(TENANT), eq(USER), any()))
                .thenReturn(List.of(VotingMobilizationPermission.builder()
                        .permissionId(91001L)
                        .subjectId(SUBJECT)
                        .tenantId(TENANT)
                        .userId(USER)
                        .buildingId(30001L)
                        .canRemind(true)
                        .status(1)
                        .build()));
        when(reminderRepository.countUnvotedOwners(SUBJECT, TENANT, 30001L)).thenReturn(7);
        when(reminderOutboxGateway.enqueueReminderRequested(any())).thenReturn(99001L);
        when(reminderRepository.insert(any())).thenAnswer(invocation -> {
            VotingMobilizationReminder reminder = invocation.getArgument(0);
            reminder.setReminderId(88001L);
            return reminder;
        });

        VotingMobilizationReminder reminder = service.sendReminder(
                new SendMobilizationReminderCommand(SUBJECT, 30001L, " 请尽快完成投票 "));

        assertEquals(88001L, reminder.getReminderId());
        assertEquals(30001L, reminder.getBuildingId());
        assertEquals(7, reminder.getTargetCount());
        assertEquals(99001L, reminder.getOutboxEventId());
        assertEquals("请尽快完成投票", reminder.getMessage());
    }

    @Test
    public void castOfflineProxyVote_withoutDynamicPermission_rejectedBeforeCast() {
        when(userContextHolder.current()).thenReturn(sysUser());
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SubjectStatus.VOTING)));
        when(ownerPropertyVotingRepository.findByOpid(OPID)).thenReturn(Optional.of(new OwnerPropertyVotingView(
                OPID, 70001L, TENANT, 30001L, new BigDecimal("80.00"), true, 1)));
        when(permissionRepository.findActiveBySubjectAndUser(eq(SUBJECT), eq(TENANT), eq(USER), any()))
                .thenReturn(List.of());

        VotingApplicationException ex = assertThrows(VotingApplicationException.class,
                () -> service.castOfflineProxyVote(new OfflineProxyVoteCommand(
                        SUBJECT, OPID, null, VoteChoice.SUPPORT, "hash-001")));

        assertEquals(VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE, ex.getReason());
        verify(voteSubmissionService, never()).cast(any());
    }

    @Test
    public void castOfflineProxyVote_withDynamicPermission_delegatesOfflineProxyChannel() {
        when(userContextHolder.current()).thenReturn(sysUser());
        when(subjectRepository.findById(SUBJECT)).thenReturn(Optional.of(subject(SubjectStatus.VOTING)));
        when(ownerPropertyVotingRepository.findByOpid(OPID)).thenReturn(Optional.of(new OwnerPropertyVotingView(
                OPID, 70001L, TENANT, 30001L, new BigDecimal("80.00"), true, 1)));
        when(permissionRepository.findActiveBySubjectAndUser(eq(SUBJECT), eq(TENANT), eq(USER), any()))
                .thenReturn(List.of(VotingMobilizationPermission.builder()
                        .permissionId(91002L)
                        .subjectId(SUBJECT)
                        .tenantId(TENANT)
                        .userId(USER)
                        .buildingId(30001L)
                        .canOfflineProxy(true)
                        .status(1)
                        .build()));
        when(voteSubmissionService.cast(any())).thenReturn(66001L);

        long voteId = service.castOfflineProxyVote(new OfflineProxyVoteCommand(
                SUBJECT, OPID, null, VoteChoice.SUPPORT, " hash-001 "));

        assertEquals(66001L, voteId);
        org.mockito.ArgumentCaptor<CastVoteCommand> captor = org.mockito.ArgumentCaptor.forClass(CastVoteCommand.class);
        verify(voteSubmissionService).cast(captor.capture());
        CastVoteCommand command = captor.getValue();
        assertEquals(70001L, command.uid());
        assertEquals(OPID, command.opid());
        assertEquals(VoteChoice.SUPPORT, command.choice());
        assertEquals("hash-001", command.signatureHash());
        assertEquals(VoteChannel.OFFLINE_PROXY, command.voteChannel());
    }
}
