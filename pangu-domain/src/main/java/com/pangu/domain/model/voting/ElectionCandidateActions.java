package com.pangu.domain.model.voting;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * 候选人资格状态机入口（纯领域逻辑，无 Spring / 持久化依赖）。
 *
 * <p>仿 {@link VotingSubjectActions} 风格，把候选人「资格审查」迁移规则内聚于此，
 * application 层只负责「取出候选人 → 调命令 → 持久化」。
 *
 * <p>状态流转矩阵（M3-3 锁定）：
 * <pre>
 *     PENDING_REVIEW --approve--&gt; APPROVED
 *     PENDING_REVIEW --reject--&gt;  REJECTED
 *     PENDING_REVIEW --withdraw--&gt; WITHDRAWN
 * </pre>
 *
 * <p>{@link CandidateStatus#APPROVED} / {@link CandidateStatus#REJECTED} /
 * {@link CandidateStatus#WITHDRAWN} 均为终态。对终态候选人再次审查一律抛
 * {@link IllegalCandidateTransitionException}，由 application 层翻译为
 * {@code VotingApplicationException}。
 */
public final class ElectionCandidateActions {

    /** 允许的状态跳转表：source → 允许的下一站集合。 */
    private static final Map<CandidateStatus, EnumSet<CandidateStatus>> TRANSITIONS;

    static {
        TRANSITIONS = new EnumMap<>(CandidateStatus.class);
        TRANSITIONS.put(CandidateStatus.PENDING_REVIEW,
                EnumSet.of(CandidateStatus.APPROVED, CandidateStatus.REJECTED, CandidateStatus.WITHDRAWN));
        TRANSITIONS.put(CandidateStatus.APPROVED,  EnumSet.noneOf(CandidateStatus.class));
        TRANSITIONS.put(CandidateStatus.REJECTED,  EnumSet.noneOf(CandidateStatus.class));
        TRANSITIONS.put(CandidateStatus.WITHDRAWN, EnumSet.noneOf(CandidateStatus.class));
    }

    private ElectionCandidateActions() {
    }

    /** PENDING_REVIEW → APPROVED。资格审查通过。 */
    public static void approve(Candidate candidate) {
        transitionTo(candidate, CandidateStatus.APPROVED);
    }

    /** PENDING_REVIEW → REJECTED。资格审查驳回。 */
    public static void reject(Candidate candidate) {
        transitionTo(candidate, CandidateStatus.REJECTED);
    }

    private static void transitionTo(Candidate candidate, CandidateStatus target) {
        Objects.requireNonNull(candidate, "candidate 不能为空");
        Objects.requireNonNull(candidate.getQualificationStatus(), "qualificationStatus 不能为空");
        ensureTransition(candidate.getQualificationStatus(), target);
        candidate.setQualificationStatus(target);
    }

    private static void ensureTransition(CandidateStatus from, CandidateStatus to) {
        EnumSet<CandidateStatus> allowed = TRANSITIONS.getOrDefault(from, EnumSet.noneOf(CandidateStatus.class));
        if (!allowed.contains(to)) {
            throw new IllegalCandidateTransitionException(
                    "非法候选人状态跳转 from=" + from + " to=" + to);
        }
    }

    /** 状态机非法跳转抛出。application 层捕获并翻译为 {@code VotingApplicationException}。 */
    public static class IllegalCandidateTransitionException extends RuntimeException {
        public IllegalCandidateTransitionException(String message) {
            super(message);
        }
    }
}
