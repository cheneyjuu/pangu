package com.pangu.application.voting;

import com.pangu.application.voting.command.CastVoteCommand;
import com.pangu.application.voting.command.OfflineProxyVoteCommand;
import com.pangu.application.voting.command.SendMobilizationReminderCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.asset.OwnerPropertyVotingView;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.VoteChannel;
import com.pangu.domain.model.voting.VotingMobilizationPermission;
import com.pangu.domain.model.voting.VotingMobilizationReminder;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.VotingMobilizationReminderRepository;
import com.pangu.domain.repository.VotingMobilizationPermissionRepository;
import com.pangu.domain.repository.VotingReminderOutboxGateway;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 投票期动员权限编排。
 *
 * <p>权限不再静态挂在角色矩阵上，而由议题进入 VOTING 事件激活，
 * 并在结算 / 撤回时失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VotingMobilizationService {

    private final VotingMobilizationPermissionRepository permissionRepository;
    private final VotingMobilizationReminderRepository reminderRepository;
    private final VotingReminderOutboxGateway reminderOutboxGateway;
    private final VotingSubjectRepository subjectRepository;
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    private final VoteSubmissionService voteSubmissionService;
    private final UserContextHolder userContextHolder;

    @Transactional
    public int activateForVotingOpened(VotingSubject subject, Instant openedAt) {
        if (subject == null || subject.getSubjectId() == null || subject.getTenantId() == null) {
            return 0;
        }
        int activated = permissionRepository.activateForSubject(
                subject.getSubjectId(),
                subject.getTenantId(),
                subject.getScope(),
                subject.getScopeReferenceId(),
                openedAt == null ? Instant.now() : openedAt,
                subject.getVoteEndAt());
        log.info("Voting mobilization permissions activated subjectId={} count={}",
                subject.getSubjectId(), activated);
        return activated;
    }

    @Transactional
    public int deactivateForSubject(Long subjectId, Instant deactivatedAt) {
        if (subjectId == null) {
            return 0;
        }
        int deactivated = permissionRepository.deactivateForSubject(
                subjectId, deactivatedAt == null ? Instant.now() : deactivatedAt);
        log.info("Voting mobilization permissions deactivated subjectId={} count={}",
                subjectId, deactivated);
        return deactivated;
    }

    public List<VotingMobilizationPermission> listMine(Long subjectId) {
        UserContext ctx = userContextHolder.current();
        requireSysUser(ctx, "查询投票动员权限");
        VotingSubject subject = requireTenantSubject(subjectId, ctx.tenantId());
        if (subject.getStatus() != SubjectStatus.VOTING) {
            return List.of();
        }
        return permissionRepository.findActiveBySubjectAndUser(
                subjectId, ctx.tenantId(), ctx.userId(), Instant.now());
    }

    @Transactional
    public VotingMobilizationReminder sendReminder(SendMobilizationReminderCommand command) {
        UserContext ctx = userContextHolder.current();
        requireSysUser(ctx, "发送投票催票");
        if (command == null || command.subjectId() == null || command.buildingId() == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "发送催票必须指定议题与楼栋");
        }
        VotingSubject subject = requireTenantSubject(command.subjectId(), ctx.tenantId());
        if (subject.getStatus() != SubjectStatus.VOTING) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_VOTING,
                    "议题不在投票中，禁止发送催票 subjectId=" + command.subjectId());
        }

        Instant now = Instant.now();
        VotingMobilizationPermission permission = permissionRepository.findActiveBySubjectAndUser(
                        command.subjectId(), ctx.tenantId(), ctx.userId(), now)
                .stream()
                .filter(p -> command.buildingId().equals(p.getBuildingId()))
                .filter(VotingMobilizationPermission::isCanRemind)
                .findFirst()
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                        "当前用户没有该楼栋催票权限 buildingId=" + command.buildingId()));

        int targetCount = reminderRepository.countUnvotedOwners(
                command.subjectId(), ctx.tenantId(), command.buildingId());
        VotingMobilizationReminder reminder = VotingMobilizationReminder.builder()
                .subjectId(command.subjectId())
                .tenantId(ctx.tenantId())
                .buildingId(command.buildingId())
                .sentByUserId(ctx.userId())
                .permissionId(permission.getPermissionId())
                .targetScope(VotingMobilizationReminder.TARGET_SCOPE_UNVOTED_BUILDING_OWNERS)
                .targetCount(targetCount)
                .messageTemplate(VotingMobilizationReminder.TEMPLATE_VOTE_REMINDER)
                .message(normalizeMessage(command.message()))
                .sentAt(now)
                .build();
        Long outboxEventId = reminderOutboxGateway.enqueueReminderRequested(reminder);
        reminder.setOutboxEventId(outboxEventId);
        VotingMobilizationReminder saved = reminderRepository.insert(reminder);
        log.info("Voting reminder requested subjectId={} buildingId={} userId={} targetCount={} outboxEventId={}",
                command.subjectId(), command.buildingId(), ctx.userId(), targetCount, outboxEventId);
        return saved;
    }

    @Transactional
    public long castOfflineProxyVote(OfflineProxyVoteCommand command) {
        UserContext ctx = userContextHolder.current();
        requireSysUser(ctx, "线下代录投票");
        if (command == null || command.subjectId() == null || command.opid() == null || command.choice() == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "线下代录必须指定议题、opid 与投票选择");
        }
        VotingSubject subject = requireTenantSubject(command.subjectId(), ctx.tenantId());
        if (subject.getStatus() != SubjectStatus.VOTING) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_VOTING_CASTABLE,
                    "议题不在投票中，禁止线下代录 subjectId=" + command.subjectId());
        }

        OwnerPropertyVotingView view = ownerPropertyVotingRepository.findByOpid(command.opid())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.OPID_NOT_OWNED,
                        "opid 不存在 opid=" + command.opid()));
        if (!ctx.tenantId().equals(view.tenantId())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.OPID_OUT_OF_SCOPE,
                    "opid 不在当前租户范围内 opid=" + command.opid());
        }
        requireOfflineProxyPermission(command.subjectId(), ctx, view.buildingId());

        CastVoteCommand castCommand = new CastVoteCommand(
                command.subjectId(),
                view.uid(),
                ctx.tenantId(),
                command.opid(),
                command.targetId(),
                command.choice(),
                normalizeMessage(command.offlineEvidenceHash()),
                VoteChannel.OFFLINE_PROXY);
        return voteSubmissionService.cast(castCommand);
    }

    private void requireSysUser(UserContext ctx, String action) {
        if (ctx == null || !ctx.isSysUser() || ctx.userId() == null || ctx.tenantId() == null) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "未识别到管理端 sys_user 上下文，禁止" + action);
        }
    }

    private VotingSubject requireTenantSubject(Long subjectId, Long tenantId) {
        VotingSubject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        if (!tenantId.equals(subject.getTenantId())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                    "议题不在当前租户范围内 subjectId=" + subjectId);
        }
        return subject;
    }

    private void requireOfflineProxyPermission(Long subjectId, UserContext ctx, Long buildingId) {
        permissionRepository.findActiveBySubjectAndUser(subjectId, ctx.tenantId(), ctx.userId(), Instant.now())
                .stream()
                .filter(p -> buildingId.equals(p.getBuildingId()))
                .filter(VotingMobilizationPermission::isCanOfflineProxy)
                .findFirst()
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                        "当前用户没有该楼栋线下代录权限 buildingId=" + buildingId));
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.strip();
    }
}
