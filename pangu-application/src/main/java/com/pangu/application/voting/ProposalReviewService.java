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
        assertRole(ELECTION_SUBMIT_ROLE, "ELECTION 议题提交初审仅限 G 端基层经办员");
        VotingSubject subject = findForUpdate(subjectId);
        if (subject.getStatus() != SubjectStatus.DRAFT) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_DRAFT,
                    "仅 DRAFT 议题可提交初审 subjectId=" + subjectId + " status=" + subject.getStatus());
        }
        SubjectStatus fromStatus = subject.getStatus();
        applyTransition(subject, VotingApplicationException.Reason.SUBJECT_NOT_DRAFT,
                () -> VotingSubjectActions.submitForCommitteeReview(subject));
        persistStatus(subject, subjectId, "submitForCommitteeReview", "SUBMIT",
                currentUserId, null, fromStatus);
        return subject;
    }

    @Transactional
    public VotingSubject committeeApprove(Long subjectId, Long currentUserId) {
        assertRole(ELECTION_COMMITTEE_REVIEW_ROLE, "ELECTION 议题居委会初审仅限居委会管理员");
        VotingSubject subject = findForUpdate(subjectId);
        if (subject.getStatus() != SubjectStatus.PENDING_COMMITTEE) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_PENDING_COMMITTEE,
                    "议题不在居委会初审中 subjectId=" + subjectId + " status=" + subject.getStatus());
        }
        SubjectStatus fromStatus = subject.getStatus();
        applyTransition(subject, VotingApplicationException.Reason.SUBJECT_NOT_PENDING_COMMITTEE,
                () -> VotingSubjectActions.committeeApprove(subject));
        persistStatus(subject, subjectId, "committeeApprove", "APPROVE",
                currentUserId, null, fromStatus);
        return subject;
    }

    @Transactional
    public VotingSubject committeeReject(Long subjectId, Long currentUserId, String reason) {
        assertRole(ELECTION_COMMITTEE_REVIEW_ROLE, "ELECTION 议题居委会初审仅限居委会管理员");
        VotingSubject subject = findForUpdate(subjectId);
        if (subject.getStatus() != SubjectStatus.PENDING_COMMITTEE) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_PENDING_COMMITTEE,
                    "议题不在居委会初审中 subjectId=" + subjectId + " status=" + subject.getStatus());
        }
        SubjectStatus fromStatus = subject.getStatus();
        applyRejectTransition(subject, VotingApplicationException.Reason.SUBJECT_NOT_PENDING_COMMITTEE,
                () -> VotingSubjectActions.committeeReject(subject, reason));
        persistStatus(subject, subjectId, "committeeReject", "REJECT",
                currentUserId, reason, fromStatus);
        return subject;
    }

    @Transactional
    public VotingSubject streetApprove(Long subjectId, Long currentUserId) {
        assertRole(ELECTION_STREET_REVIEW_ROLE, "ELECTION 议题街道终审仅限街道办");
        VotingSubject subject = findForUpdate(subjectId);
        if (subject.getStatus() != SubjectStatus.PENDING_STREET) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_PENDING_STREET,
                    "议题不在街道办终审中 subjectId=" + subjectId + " status=" + subject.getStatus());
        }
        CandidatePoolSnapshot pool = electionCandidateRegistry.countActivePool(subjectId);
        if (pool.eligibleCount() == 0) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.ELECTION_NO_APPROVED_CANDIDATE,
                    "选举议题至少需要 1 名通过资格审查的候选人才能终审通过 subjectId=" + subjectId);
        }
        SubjectStatus fromStatus = subject.getStatus();
        applyTransition(subject, VotingApplicationException.Reason.SUBJECT_NOT_PENDING_STREET,
                () -> VotingSubjectActions.streetApprove(subject));
        persistStatus(subject, subjectId, "streetApprove", "APPROVE",
                currentUserId, null, fromStatus);
        return subject;
    }

    @Transactional
    public VotingSubject streetReject(Long subjectId, Long currentUserId, String reason) {
        assertRole(ELECTION_STREET_REVIEW_ROLE, "ELECTION 议题街道终审仅限街道办");
        VotingSubject subject = findForUpdate(subjectId);
        if (subject.getStatus() != SubjectStatus.PENDING_STREET) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.SUBJECT_NOT_PENDING_STREET,
                    "议题不在街道办终审中 subjectId=" + subjectId + " status=" + subject.getStatus());
        }
        SubjectStatus fromStatus = subject.getStatus();
        applyRejectTransition(subject, VotingApplicationException.Reason.SUBJECT_NOT_PENDING_STREET,
                () -> VotingSubjectActions.streetReject(subject, reason));
        persistStatus(subject, subjectId, "streetReject", "REJECT",
                currentUserId, reason, fromStatus);
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

    private void applyTransition(VotingSubject subject,
                                 VotingApplicationException.Reason reason,
                                 Runnable action) {
        try {
            action.run();
        } catch (VotingSubjectActions.IllegalSubjectTransitionException e) {
            throw new VotingApplicationException(reason, e.getMessage(), e);
        }
    }

    private void applyRejectTransition(VotingSubject subject,
                                       VotingApplicationException.Reason stateReason,
                                       Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            throw new VotingApplicationException(
                    VotingApplicationException.Reason.REVIEW_REJECT_REASON_REQUIRED,
                    e.getMessage(), e);
        } catch (VotingSubjectActions.IllegalSubjectTransitionException e) {
            throw new VotingApplicationException(stateReason, e.getMessage(), e);
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
}
