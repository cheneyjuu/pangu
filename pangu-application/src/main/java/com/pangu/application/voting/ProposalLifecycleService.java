package com.pangu.application.voting;

import com.pangu.application.voting.command.CancelSubjectCommand;
import com.pangu.application.voting.command.ProposeSubjectCommand;
import com.pangu.application.voting.command.PublishSubjectCommand;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
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

/**
 * 议题生命周期编排服务（M3-2 引入）。
 *
 * <p>本服务把"立项-公示-撤回-开票（scheduler 调）-业主可见列表"5 条命令编排在一起，
 * 与 {@link VotingApplicationService#settle} 沿用既定的"行锁 → 状态变更 → 乐观锁"模式。
 *
 * <p>角色授权决策由 service 层完成：endpoint 上的 {@code @PreAuthorize} 仅做粗筛
 * （{@code voting:subject:create / publish / cancel}），是否真正可以发起 / 撤回还要根据
 * {@link SubjectType} 与议题状态进一步判定。这个分层与 M2-3 disclosure 的做法一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalLifecycleService {

    private final VotingSubjectRepository subjectRepository;
    private final OwnerPropertyVotingRepository ownerPropertyVotingRepository;

    /**
     * 立项（DRAFT 写入）。SubjectType=ELECTION 一律拒绝（M3-3 才放开）。
     */
    @Transactional
    public VotingSubject propose(ProposeSubjectCommand cmd) {
        if (cmd.subjectType() == SubjectType.ELECTION) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_TYPE_NOT_SUPPORTED,
                    "ELECTION 类型立项暂不支持，将在 M3-3 放开");
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
        } catch (IllegalArgumentException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    "立项参数非法：" + e.getMessage(), e);
        }
        VotingSubject persisted = subjectRepository.insert(draft);
        log.info("Subject proposed subjectId={} type={} scope={} proposedByUserId={}",
                persisted.getSubjectId(), persisted.getSubjectType(),
                persisted.getScope(), persisted.getProposedByUserId());
        return persisted;
    }

    /**
     * 公示：DRAFT → PUBLISHED。
     */
    @Transactional
    public VotingSubject publish(PublishSubjectCommand cmd) {
        VotingSubject subject = subjectRepository.findByIdForUpdate(cmd.subjectId())
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + cmd.subjectId()));
        if (subject.getStatus() != SubjectStatus.DRAFT) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_DRAFT,
                    "议题不在草稿状态 subjectId=" + cmd.subjectId() + " status=" + subject.getStatus());
        }
        try {
            VotingSubjectActions.publish(subject);
        } catch (VotingSubjectActions.IllegalSubjectTransitionException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_DRAFT, e.getMessage(), e);
        }
        int updated = subjectRepository.updateStatus(
                subject.getSubjectId(), SubjectStatus.PUBLISHED.getDbValue(), subject.getVersion());
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CONCURRENT_LIFECYCLE_MODIFICATION,
                    "议题在公示过程中被并发修改 subjectId=" + cmd.subjectId());
        }
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
        VotingSubject subject = subjectRepository.findByIdForUpdate(subjectId)
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        if (subject.getStatus() != SubjectStatus.PUBLISHED) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_PUBLISHED,
                    "议题不在公示状态 subjectId=" + subjectId + " status=" + subject.getStatus());
        }
        try {
            VotingSubjectActions.openVoting(subject, now);
        } catch (VotingSubjectActions.IllegalSubjectTransitionException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_PUBLISHED, e.getMessage(), e);
        }
        int updated = subjectRepository.updateStatus(
                subjectId, SubjectStatus.VOTING.getDbValue(), subject.getVersion());
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CONCURRENT_LIFECYCLE_MODIFICATION,
                    "议题在开票过程中被并发修改 subjectId=" + subjectId);
        }
        log.info("Subject voting opened subjectId={} now={}", subjectId, now);
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
}
