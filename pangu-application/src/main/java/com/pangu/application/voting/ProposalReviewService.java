package com.pangu.application.voting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.context.UserContext;
import com.pangu.domain.context.UserContextHolder;
import com.pangu.domain.model.voting.CandidatePoolSnapshot;
import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingSubject;
import com.pangu.domain.model.voting.VotingSubjectActions;
import com.pangu.domain.repository.ElectionCandidateRegistry;
import com.pangu.domain.repository.VotingSubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ELECTION 议题双签审批编排（梯度 B）。
 *
 * <p>候选人审查 / Waiver 审批仍保持独立状态机；本服务只负责议题级
 * DRAFT → PENDING_COMMITTEE → PENDING_STREET → PUBLISHED 主链路。
 *
 * <p>状态流转借鉴 {@code CampaignApplyService} 的动作路由思路，但不采用自动 while 推进：
 * 选举审批每一步都必须由不同真实角色手工签署，因此 public 方法只触发一个明确动作，
 * 公共的角色校验、状态校验、领域动作、审计落库和乐观锁持久化由统一模板执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalReviewService {

    private static final ObjectMapper REVIEW_HISTORY_MAPPER = new ObjectMapper();
    private static final String ELECTION_SUBMIT_ROLE = "GOV_OPERATOR";
    private static final String ELECTION_COMMITTEE_REVIEW_ROLE = "COMMUNITY_ADMIN";
    private static final String ELECTION_STREET_REVIEW_ROLE = "GOV_SUPER_ADMIN";

    private final VotingSubjectRepository subjectRepository;
    private final ElectionCandidateRegistry electionCandidateRegistry;
    private final UserContextHolder userContextHolder;

    @Transactional
    public VotingSubject submitForCommitteeReview(Long subjectId, Long currentUserId) {
        return executeReview(ReviewAction.SUBMIT_FOR_COMMITTEE_REVIEW, subjectId, currentUserId, null);
    }

    @Transactional
    public VotingSubject committeeApprove(Long subjectId, Long currentUserId) {
        return executeReview(ReviewAction.COMMITTEE_APPROVE, subjectId, currentUserId, null);
    }

    @Transactional
    public VotingSubject committeeReject(Long subjectId, Long currentUserId, String reason) {
        return executeReview(ReviewAction.COMMITTEE_REJECT, subjectId, currentUserId, reason);
    }

    @Transactional
    public VotingSubject streetApprove(Long subjectId, Long currentUserId) {
        return executeReview(ReviewAction.STREET_APPROVE, subjectId, currentUserId, null);
    }

    @Transactional
    public VotingSubject streetReject(Long subjectId, Long currentUserId, String reason) {
        return executeReview(ReviewAction.STREET_REJECT, subjectId, currentUserId, reason);
    }

    private VotingSubject executeReview(ReviewAction action,
                                        Long subjectId,
                                        Long currentUserId,
                                        String reason) {
        assertRole(action.requiredRole, action.forbiddenMessage);
        VotingSubject subject = findForUpdate(subjectId);
        assertExpectedStatus(action, subject, subjectId);
        if (action.requiresApprovedCandidate) {
            assertApprovedCandidate(subjectId);
        }

        SubjectStatus fromStatus = subject.getStatus();
        applyTransition(action, subject, reason);
        persistStatus(subject, subjectId, action.auditAction, action.decision,
                currentUserId, action.reject ? reason : null, fromStatus);
        return subject;
    }

    private VotingSubject findForUpdate(Long subjectId) {
        VotingSubject subject = subjectRepository.findByIdForUpdate(subjectId)
                .orElseThrow(() -> new VotingApplicationException(
                        VotingApplicationException.Reason.SUBJECT_NOT_FOUND,
                        "议题不存在 subjectId=" + subjectId));
        if (subject.getSubjectType() != SubjectType.ELECTION) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_TYPE_NOT_SUPPORTED,
                    "仅 ELECTION 议题需要双签审批 subjectId=" + subjectId
                            + " subjectType=" + subject.getSubjectType());
        }
        return subject;
    }

    private void assertRole(String expectedRole, String message) {
        UserContext ctx = userContextHolder.current();
        if (ctx == null || !expectedRole.equals(ctx.roleKey())) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.PROPOSE_FORBIDDEN_FOR_TYPE,
                    message + "，当前角色=" + (ctx == null ? "ANONYMOUS" : ctx.roleKey()));
        }
    }

    private void assertExpectedStatus(ReviewAction action, VotingSubject subject, Long subjectId) {
        if (subject.getStatus() == action.expectedStatus) {
            return;
        }
        throw new VotingApplicationException(
                action.stateReason,
                action.statusErrorMessage(subjectId, subject.getStatus()));
    }

    private void assertApprovedCandidate(Long subjectId) {
        CandidatePoolSnapshot pool = electionCandidateRegistry.countActivePool(subjectId);
        if (pool.eligibleCount() == 0) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.ELECTION_NO_APPROVED_CANDIDATE,
                    "选举议题至少需要 1 名通过资格审查的候选人才能终审通过 subjectId=" + subjectId);
        }
    }

    private void applyTransition(ReviewAction action, VotingSubject subject, String reason) {
        try {
            action.transition.apply(subject, reason);
        } catch (IllegalArgumentException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.REVIEW_REJECT_REASON_REQUIRED,
                    e.getMessage(), e);
        } catch (VotingSubjectActions.IllegalSubjectTransitionException e) {
            throw new VotingApplicationException(action.stateReason, e.getMessage(), e);
        }
    }

    private void persistStatus(VotingSubject subject,
                               Long subjectId,
                               String action,
                               String decision,
                               Long currentUserId,
                               String reason,
                               SubjectStatus fromStatus) {
        String reviewEntryJson = reviewEntryJson(
                action, decision, currentUserId, reason, fromStatus, subject.getStatus());
        int updated = subjectRepository.updateStatusWithReviewHistory(
                subjectId, subject.getStatus().getDbValue(), subject.getVersion(), reviewEntryJson);
        if (updated != 1) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.CONCURRENT_LIFECYCLE_MODIFICATION,
                    "议题审批过程中被并发修改 subjectId=" + subjectId);
        }
        log.info("Subject review action={} subjectId={} byUserId={} status={}",
                action, subjectId, currentUserId, subject.getStatus());
    }

    private String reviewEntryJson(String action,
                                   String decision,
                                   Long reviewerUserId,
                                   String reason,
                                   SubjectStatus fromStatus,
                                   SubjectStatus toStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("decision", decision);
        payload.put("reviewerUserId", reviewerUserId);
        payload.put("reason", reason);
        payload.put("reviewedAt", Instant.now().toString());
        payload.put("fromStatus", fromStatus.name());
        payload.put("toStatus", toStatus.name());
        try {
            return REVIEW_HISTORY_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "审批审计轨迹序列化失败 action=" + action + " subjectStatus=" + toStatus, e);
        }
    }

    private enum ReviewAction {
        SUBMIT_FOR_COMMITTEE_REVIEW(
                ELECTION_SUBMIT_ROLE,
                "ELECTION 议题提交初审仅限 G 端基层经办员",
                SubjectStatus.DRAFT,
                VotingApplicationException.Reason.SUBJECT_NOT_DRAFT,
                "submitForCommitteeReview",
                "SUBMIT",
                false,
                false,
                (subject, reason) -> VotingSubjectActions.submitForCommitteeReview(subject)),
        COMMITTEE_APPROVE(
                ELECTION_COMMITTEE_REVIEW_ROLE,
                "ELECTION 议题居委会初审仅限居委会管理员",
                SubjectStatus.PENDING_COMMITTEE,
                VotingApplicationException.Reason.SUBJECT_NOT_PENDING_COMMITTEE,
                "committeeApprove",
                "APPROVE",
                false,
                false,
                (subject, reason) -> VotingSubjectActions.committeeApprove(subject)),
        COMMITTEE_REJECT(
                ELECTION_COMMITTEE_REVIEW_ROLE,
                "ELECTION 议题居委会初审仅限居委会管理员",
                SubjectStatus.PENDING_COMMITTEE,
                VotingApplicationException.Reason.SUBJECT_NOT_PENDING_COMMITTEE,
                "committeeReject",
                "REJECT",
                true,
                false,
                VotingSubjectActions::committeeReject),
        STREET_APPROVE(
                ELECTION_STREET_REVIEW_ROLE,
                "ELECTION 议题街道终审仅限街道办",
                SubjectStatus.PENDING_STREET,
                VotingApplicationException.Reason.SUBJECT_NOT_PENDING_STREET,
                "streetApprove",
                "APPROVE",
                false,
                true,
                (subject, reason) -> VotingSubjectActions.streetApprove(subject)),
        STREET_REJECT(
                ELECTION_STREET_REVIEW_ROLE,
                "ELECTION 议题街道终审仅限街道办",
                SubjectStatus.PENDING_STREET,
                VotingApplicationException.Reason.SUBJECT_NOT_PENDING_STREET,
                "streetReject",
                "REJECT",
                true,
                false,
                VotingSubjectActions::streetReject);

        private final String requiredRole;
        private final String forbiddenMessage;
        private final SubjectStatus expectedStatus;
        private final VotingApplicationException.Reason stateReason;
        private final String auditAction;
        private final String decision;
        private final boolean reject;
        private final boolean requiresApprovedCandidate;
        private final ReviewTransition transition;

        ReviewAction(String requiredRole,
                     String forbiddenMessage,
                     SubjectStatus expectedStatus,
                     VotingApplicationException.Reason stateReason,
                     String auditAction,
                     String decision,
                     boolean reject,
                     boolean requiresApprovedCandidate,
                     ReviewTransition transition) {
            this.requiredRole = requiredRole;
            this.forbiddenMessage = forbiddenMessage;
            this.expectedStatus = expectedStatus;
            this.stateReason = stateReason;
            this.auditAction = auditAction;
            this.decision = decision;
            this.reject = reject;
            this.requiresApprovedCandidate = requiresApprovedCandidate;
            this.transition = transition;
        }

        private String statusErrorMessage(Long subjectId, SubjectStatus actualStatus) {
            return switch (this) {
                case SUBMIT_FOR_COMMITTEE_REVIEW ->
                        "仅 DRAFT 议题可提交初审 subjectId=" + subjectId + " status=" + actualStatus;
                case COMMITTEE_APPROVE, COMMITTEE_REJECT ->
                        "议题不在居委会初审中 subjectId=" + subjectId + " status=" + actualStatus;
                case STREET_APPROVE, STREET_REJECT ->
                        "议题不在街道办终审中 subjectId=" + subjectId + " status=" + actualStatus;
            };
        }
    }

    @FunctionalInterface
    private interface ReviewTransition {
        void apply(VotingSubject subject, String reason);
    }
}
