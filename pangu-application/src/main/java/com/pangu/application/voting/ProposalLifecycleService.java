package com.pangu.application.voting;

import com.pangu.application.handover.HandoverCircuitBreaker;
import com.pangu.application.support.ApplicationRoleGuard;
import com.pangu.application.voting.command.CancelSubjectCommand;
import com.pangu.application.voting.command.ProposeSubjectCommand;
import com.pangu.application.voting.command.PublishSubjectCommand;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingDenominatorResolver;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import com.pangu.domain.repository.OwnerPropertyVotingRepository;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 议题生命周期编排服务（M3-2 引入）。
 *
 * <p>本服务把"立项-公示-撤回-开票（scheduler 调）-业主可见列表"5 条命令编排在一起，
 * 与 {@link VotingApplicationService#settle} 沿用既定的"行锁 → 状态变更 → 乐观锁"模式。
 *
 * <p>角色授权决策由 service 层完成：endpoint 上的 {@code @PreAuthorize} 仅做粗筛
 * （{@code voting:subject:create / publish / cancel}），是否真正可以发起 / 撤回还要根据
 * {@link SubjectType} 与议题状态进一步判定。这个分层与 M2-3 disclosure 的做法一致。
 *
 * <p>公示 / 开票使用显式动作定义加统一执行模板；scheduler 开票仍只触发单步动作，
 * 不把后续结算自动串进本服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalLifecycleService {

    private final VotingSubjectRepository subjectRepository;
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;
    private final HandoverCircuitBreaker handoverCircuitBreaker;
    private final VotingDenominatorResolver denominatorResolver;
    private final UserContextHolder userContextHolder;
    private final VotingMobilizationService votingMobilizationService;

    /**
     * 选举议题（ELECTION）立项执行人：G 端基层经办员。
     *
     * <p>设计稿《选举闭环.md》§二·阶段一明确：换届选举新建必须由
     * {@code role_key = GOV_OPERATOR} 且隶属居委会 / 网格组织的 G 端经办人操作。
     */
    private static final String ELECTION_PROPOSE_ROLE = "GOV_OPERATOR";
    private static final Set<Integer> ELECTION_PROPOSE_DEPT_TYPES = Set.of(2, 5);

    /**
     * 立项（DRAFT 写入）。M3-3 起放开 ELECTION：要求携带 maxWinners >= 1。
     *
     * <p>M5 起 ELECTION 额外加角色护栏：仅 G 端 GOV_OPERATOR 可立项选举
     * （业委会全员封死、党组书记 / 居委会管理员 / 街道办不立项）。
     */
    @Transactional
    public VotingSubject propose(ProposeSubjectCommand cmd) {
        if (cmd.subjectType() == SubjectType.ELECTION
                && (cmd.maxWinners() == null || cmd.maxWinners() < 1)) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.ELECTION_MAX_WINNERS_REQUIRED,
                    "ELECTION 立项必须携带 maxWinners >= 1，当前 maxWinners=" + cmd.maxWinners());
        }
        if (cmd.subjectType() == SubjectType.ELECTION) {
            UserContext ctx = userContextHolder.current();
            if (!canProposeElection(ctx)) {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                        "ELECTION 议题立项仅限 G 端基层经办员（dept_type IN (2,5)），当前角色="
                                + ApplicationRoleGuard.currentRole(ctx)
                                + " deptType=" + (ctx == null ? null : ctx.deptType()));
            }
        }
        VotingSubject draft;
        try {
            draft = VotingSubjectActions.open(
                    cmd.tenantId(),
                    cmd.subjectType(),
                    cmd.scope(),
                    cmd.scopeReferenceId(),
                    cmd.title(),
                    cmd.voteStartAt(),
                    cmd.voteEndAt(),
                    cmd.proposedByUserId(),
                    cmd.partyRatioFloor());
            draft.setContent(normalizeContent(cmd.content(), cmd.subjectType()));
        } catch (IllegalArgumentException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "立项参数非法：" + e.getMessage(), e);
        }
        // maxWinners 仅 ELECTION 透传落库（GENERAL/MAJOR 为 null，trigger 13 不约束非 ELECTION）
        if (cmd.subjectType() == SubjectType.ELECTION) {
            draft.setMaxWinners(cmd.maxWinners());
        }
        // 换届熔断：换届选举在途期间冻结新 GENERAL/MAJOR 立项（放行 ELECTION，避免下一届换届自我死锁）
        if (cmd.subjectType() != SubjectType.ELECTION) {
            handoverCircuitBreaker.activeElectionSubjectId(cmd.tenantId()).ifPresent(electionId -> {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.PROPOSE_FROZEN_HANDOVER,
                        "换届选举进行中，新议题立项已熔断 electionSubjectId=" + electionId);
            });
        }
        VotingSubject persisted = subjectRepository.insert(draft);
        if (persisted.getSubjectType() == SubjectType.ELECTION) {
            try {
                denominatorResolver.resolve(persisted);
            } catch (RuntimeException ex) {
                throw new VotingApplicationException(
                        VotingApplicationException.Reason.DENOMINATOR_RESOLVE_FAILED,
                        "ELECTION 立项分母快照落定失败 subjectId=" + persisted.getSubjectId(), ex);
            }
        }
        log.info("Subject proposed subjectId={} type={} scope={} maxWinners={} proposedByUserId={}",
                persisted.getSubjectId(), persisted.getSubjectType(),
                persisted.getScope(), persisted.getMaxWinners(), persisted.getProposedByUserId());
        return persisted;
    }

    private String normalizeContent(String content, SubjectType subjectType) {
        String normalized = content == null ? null : content.trim();
        if (subjectType != SubjectType.ELECTION && (normalized == null || normalized.isBlank())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "GENERAL/MAJOR 议题立项必须填写正文");
        }
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("<script")
                || lower.contains("javascript:")
                || lower.matches("(?s).*\\son[a-z]+\\s*=.*")) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "议题正文包含不允许的富文本内容");
        }
        return normalized;
    }

    /**
     * 公示：DRAFT → PUBLISHED（仅 GENERAL/MAJOR 等非 ELECTION 议题）。
     *
     * <p>ELECTION 议题必须走 {@link ProposalReviewService#streetApprove(Long, Long)}，
     * 由 {@code voting:subject:review:street} 完成街道终审、状态发布和 review_history 留痕。
     */
    @Transactional
    public VotingSubject publish(PublishSubjectCommand cmd) {
        VotingSubject subject = executeLifecycleTransition(
                LifecycleTransition.PUBLISH, cmd.subjectId(), null);
        log.info("Subject published subjectId={} byUserId={}", cmd.subjectId(), cmd.currentUserId());
        return subject;
    }

    /**
     * 撤回：根据 {@code byGovernment} 路由到发起者本人撤 / 政府强撤。
     */
    @Transactional
    public VotingSubject cancel(CancelSubjectCommand cmd) {
        VotingSubject subject = subjectRepository.findByIdForUpdate(cmd.subjectId())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + cmd.subjectId()));

        // VOTING / CLOSED / SETTLED / CANCELLED 一律拒绝
        SubjectStatus status = subject.getStatus();
        if (status == SubjectStatus.VOTING || status == SubjectStatus.CLOSED
                || status == SubjectStatus.SETTLED || status == SubjectStatus.CANCELLED) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANCEL_FORBIDDEN,
                    "议题已进入 " + status + " 状态，不可撤回 subjectId=" + cmd.subjectId());
        }

        Instant now = Instant.now();
        try {
            if (cmd.byGovernment()) {
                VotingSubjectActions.cancelByGovernment(subject, cmd.currentUserId(), cmd.reason(), now);
            } else {
                VotingSubjectActions.cancelByProposer(subject, cmd.currentUserId(), cmd.reason(), now);
            }
        } catch (VotingSubjectActions.IllegalSubjectTransitionException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANCEL_FORBIDDEN, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CANCEL_FORBIDDEN, e.getMessage(), e);
        }

        int updated = subjectRepository.cancel(subject, subject.getVersion());
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CONCURRENT_LIFECYCLE_MODIFICATION,
                    "议题在撤回过程中被并发修改 subjectId=" + cmd.subjectId());
        }
        votingMobilizationService.deactivateForSubject(subject.getSubjectId(), now);
        log.info("Subject cancelled subjectId={} byUserId={} byGovernment={} reasonLen={}",
                cmd.subjectId(), cmd.currentUserId(), cmd.byGovernment(),
                cmd.reason() == null ? 0 : cmd.reason().length());
        return subject;
    }

    /**
     * Scheduler 入口：PUBLISHED → VOTING。要求当前时间 ≥ {@code vote_start_at}。
     */
    @Transactional
    public VotingSubject openVoting(Long subjectId, Instant now) {
        VotingSubject subject = executeLifecycleTransition(LifecycleTransition.OPEN_VOTING, subjectId, now);
        votingMobilizationService.activateForVotingOpened(subject, now);
        log.info("Subject voting opened subjectId={} now={}", subjectId, now);
        return subject;
    }

    private VotingSubject executeLifecycleTransition(LifecycleTransition action,
                                                    Long subjectId,
                                                    Instant now) {
        VotingSubject subject = subjectRepository.findByIdForUpdate(subjectId)
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        action.validateSubject(subject, subjectId);
        try {
            action.transition.apply(subject, now);
        } catch (VotingSubjectActions.IllegalSubjectTransitionException e) {
            throw new VotingApplicationException(
                    action.invalidTransitionReason, e.getMessage(), e);
        }
        int updated = subjectRepository.updateStatus(
                subjectId, action.targetStatus.getDbValue(), subject.getVersion());
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CONCURRENT_LIFECYCLE_MODIFICATION,
                    action.concurrentErrorMessage(subjectId));
        }
        return subject;
    }

    /**
     * "我的议题"：返回业主可见议题列表（C 端）。
     *
     * <p>过滤规则：
     * <ul>
     *   <li>租户 = 当前业主激活租户；</li>
     *   <li>状态 ∈ {PUBLISHED, VOTING, CLOSED, SETTLED}（DRAFT/CANCELLED 不曝光）；</li>
     *   <li>scope=COMMUNITY，或 scope=BUILDING 且 building_id 属于业主名下楼栋。</li>
     * </ul>
     */
    public List<VotingSubject> findVisibleForOwner(Long uid, Long tenantId, int page, int size) {
        if (uid == null || tenantId == null) {
            return Collections.emptyList();
        }
        if (page < 0) page = 0;
        if (size <= 0 || size > 200) size = 20;
        List<Long> buildingIds = ownerPropertyVotingRepository.findBuildingIdsByUid(uid, tenantId);
        return subjectRepository.findVisibleForOwner(tenantId, buildingIds, size, page * size);
    }

    private boolean canProposeElection(UserContext ctx) {
        return ctx != null
                && ELECTION_PROPOSE_ROLE.equals(ctx.roleKey())
                && ELECTION_PROPOSE_DEPT_TYPES.contains(ctx.deptType());
    }

    private enum LifecycleTransition {
        PUBLISH(
                SubjectStatus.DRAFT,
                SubjectStatus.PUBLISHED,
                VotingApplicationException.Reason.SUBJECT_NOT_DRAFT,
                "公示",
                (subject, now) -> VotingSubjectActions.publish(subject)) {
            @Override
            void validateSubject(VotingSubject subject, Long subjectId) {
                if (subject.getSubjectType() == SubjectType.ELECTION) {
                    throw new VotingApplicationException(
                            VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                            "ELECTION 议题必须通过街道办终审发布，请使用 street-review subjectId=" + subjectId);
                }
                super.validateSubject(subject, subjectId);
            }
        },
        OPEN_VOTING(
                SubjectStatus.PUBLISHED,
                SubjectStatus.VOTING,
                VotingApplicationException.Reason.SUBJECT_NOT_PUBLISHED,
                "开票",
                VotingSubjectActions::openVoting);

        private final SubjectStatus expectedStatus;
        private final SubjectStatus targetStatus;
        private final VotingApplicationException.Reason invalidTransitionReason;
        private final String actionName;
        private final LifecycleTransitionApplier transition;

        LifecycleTransition(SubjectStatus expectedStatus,
                            SubjectStatus targetStatus,
                            VotingApplicationException.Reason invalidTransitionReason,
                            String actionName,
                            LifecycleTransitionApplier transition) {
            this.expectedStatus = expectedStatus;
            this.targetStatus = targetStatus;
            this.invalidTransitionReason = invalidTransitionReason;
            this.actionName = actionName;
            this.transition = transition;
        }

        void validateSubject(VotingSubject subject, Long subjectId) {
            if (subject.getStatus() == expectedStatus) {
                return;
            }
            throw new VotingApplicationException(
                    invalidTransitionReason,
                    statusErrorMessage(subjectId, subject.getStatus()));
        }

        private String statusErrorMessage(Long subjectId, SubjectStatus actualStatus) {
            return switch (this) {
                case PUBLISH -> "议题不在草稿状态 subjectId=" + subjectId + " status=" + actualStatus;
                case OPEN_VOTING -> "议题不在公示状态 subjectId=" + subjectId + " status=" + actualStatus;
            };
        }

        private String concurrentErrorMessage(Long subjectId) {
            return "议题在" + actionName + "过程中被并发修改 subjectId=" + subjectId;
        }
    }

    @FunctionalInterface
    private interface LifecycleTransitionApplier {
        void apply(VotingSubject subject, Instant now);
    }
}
