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
 * <p>状态流转矩阵（本期插入党组书记前置审查后两段化）：
 * <pre>
 *     PENDING_PARTY_REVIEW     --partyApprove--&gt; PENDING_COMMITTEE_REVIEW   党组书记前置审查通过
 *     PENDING_PARTY_REVIEW     --reject--&gt;       REJECTED                   党组书记前置审查驳回
 *     PENDING_PARTY_REVIEW     --withdraw--&gt;     WITHDRAWN
 *     PENDING_COMMITTEE_REVIEW --approve--&gt;      APPROVED                   居委会资格审查通过
 *     PENDING_COMMITTEE_REVIEW --reject--&gt;       REJECTED                   居委会资格审查驳回
 *     PENDING_COMMITTEE_REVIEW --withdraw--&gt;     WITHDRAWN
 * </pre>
 *
 * <p>{@code approve}（→APPROVED）现在**只**能从 {@link CandidateStatus#PENDING_COMMITTEE_REVIEW}
 * 触发——候选人必须先过党组前置审查（{@code partyApprove}）才能被居委会资格审查通过；对仍处
 * {@link CandidateStatus#PENDING_PARTY_REVIEW} 的候选人直接 {@code approve} 会被状态机拒绝。
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
        TRANSITIONS.put(CandidateStatus.PENDING_PARTY_REVIEW,
                EnumSet.of(CandidateStatus.PENDING_COMMITTEE_REVIEW,
                        CandidateStatus.REJECTED, CandidateStatus.WITHDRAWN));
        TRANSITIONS.put(CandidateStatus.PENDING_COMMITTEE_REVIEW,
                EnumSet.of(CandidateStatus.APPROVED, CandidateStatus.REJECTED, CandidateStatus.WITHDRAWN));
        TRANSITIONS.put(CandidateStatus.APPROVED,  EnumSet.noneOf(CandidateStatus.class));
        TRANSITIONS.put(CandidateStatus.REJECTED,  EnumSet.noneOf(CandidateStatus.class));
        TRANSITIONS.put(CandidateStatus.WITHDRAWN, EnumSet.noneOf(CandidateStatus.class));
    }

    private ElectionCandidateActions() {
    }

    /** PENDING_PARTY_REVIEW → PENDING_COMMITTEE_REVIEW。党组书记前置审查通过，移交居委会资格审查。 */
    public static void partyApprove(Candidate candidate) {
        transitionTo(candidate, CandidateStatus.PENDING_COMMITTEE_REVIEW);
    }

    /** PENDING_PARTY_REVIEW → REJECTED。党组书记前置审查驳回（仅党组前置审查阶段可调）。 */
    public static void partyReject(Candidate candidate) {
        requireCurrent(candidate, CandidateStatus.PENDING_PARTY_REVIEW);
        transitionTo(candidate, CandidateStatus.REJECTED);
    }

    /** PENDING_COMMITTEE_REVIEW → APPROVED。居委会资格审查通过。 */
    public static void approve(Candidate candidate) {
        transitionTo(candidate, CandidateStatus.APPROVED);
    }

    /** PENDING_COMMITTEE_REVIEW → REJECTED。居委会资格审查驳回（仅资格审查阶段可调）。 */
    public static void reject(Candidate candidate) {
        requireCurrent(candidate, CandidateStatus.PENDING_COMMITTEE_REVIEW);
        transitionTo(candidate, CandidateStatus.REJECTED);
    }

    private static void transitionTo(Candidate candidate, CandidateStatus target) {
        Objects.requireNonNull(candidate, "candidate 不能为空");
        Objects.requireNonNull(candidate.getQualificationStatus(), "qualificationStatus 不能为空");
        ensureTransition(candidate.getQualificationStatus(), target);
        candidate.setQualificationStatus(target);
    }

    /**
     * 显式源态守卫：两个驳回动作（partyReject / reject）都落到同一终态 REJECTED，
     * 单凭 (from → to) 跳转表无法区分阶段归属，故在此点名各自唯一允许的源态。
     */
    private static void requireCurrent(Candidate candidate, CandidateStatus expected) {
        Objects.requireNonNull(candidate, "candidate 不能为空");
        if (candidate.getQualificationStatus() != expected) {
            throw new IllegalCandidateTransitionException(
                    "驳回动作要求当前状态=" + expected + "，实际=" + candidate.getQualificationStatus());
        }
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
