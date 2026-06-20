package com.pangu.domain.model.voting;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * 议题聚合根状态机入口（纯领域逻辑，无 Spring / 持久化依赖）。
 *
 * <p>本类把 M3-2 引入的"立项-公示-开票-撤回"四条命令凝聚到 {@link VotingSubject} 旁，
 * 避免聚合根本身臃肿；application 层只负责"取出聚合 → 调命令 → 持久化"，业务规则
 * 全部内聚在这里。
 *
 * <p>状态流转矩阵（M3-2 锁定）：
 * <pre>
 *     DRAFT      --publish--&gt;     PUBLISHED
 *     DRAFT      --cancel(self)--&gt; CANCELLED
 *     PUBLISHED  --openVoting--&gt;  VOTING       (scheduler 自动)
 *     PUBLISHED  --cancel(gov)--&gt; CANCELLED
 *     VOTING     --(deadline)--&gt;  CLOSED       (VotingDeadlineScheduler 已实现)
 *     CLOSED     --settle--&gt;     SETTLED       (VotingApplicationService.settle 已实现)
 * </pre>
 *
 * <p>非法跳转一律抛 {@link IllegalSubjectTransitionException}，由 application 层翻译为
 * {@code VotingApplicationException}（M3-2 新增 Reason）。
 */
public final class VotingSubjectActions {

    /** 允许的状态跳转表：source → 允许的下一站集合。 */
    private static final Map<SubjectStatus, EnumSet<SubjectStatus>> TRANSITIONS;

    static {
        TRANSITIONS = new EnumMap<>(SubjectStatus.class);
        TRANSITIONS.put(SubjectStatus.DRAFT,     EnumSet.of(SubjectStatus.PUBLISHED, SubjectStatus.CANCELLED));
        TRANSITIONS.put(SubjectStatus.PUBLISHED, EnumSet.of(SubjectStatus.VOTING,    SubjectStatus.CANCELLED));
        TRANSITIONS.put(SubjectStatus.VOTING,    EnumSet.of(SubjectStatus.CLOSED));
        TRANSITIONS.put(SubjectStatus.CLOSED,    EnumSet.of(SubjectStatus.SETTLED));
        TRANSITIONS.put(SubjectStatus.SETTLED,   EnumSet.noneOf(SubjectStatus.class));
        TRANSITIONS.put(SubjectStatus.CANCELLED, EnumSet.noneOf(SubjectStatus.class));
    }

    private VotingSubjectActions() {
    }

    /**
     * 工厂：发起一项新议题（status=DRAFT）。
     *
     * @param tenantId            租户 ID（必填）
     * @param subjectType         议题类型（M3-2 仅放行 GENERAL/MAJOR；ELECTION 由 application 层提前 reject）
     * @param scope               分母范围
     * @param scopeReferenceId    范围引用 ID（COMMUNITY 时可为 null；BUILDING 必填）
     * @param title               议题标题
     * @param voteStartAt         投票开始时间（必填，必须晚于现在；scheduler 用其触发 PUBLISHED→VOTING）
     * @param voteEndAt           投票截止时间（必填，必须晚于 voteStartAt）
     * @param proposedByUserId    发起人 sys_user.user_id（必填）
     * @param partyRatioFloor     党员比例下限（可空，默认 0.50；MAJOR 类型由 application 层填充实际值）
     */
    public static VotingSubject open(Long tenantId,
                                      SubjectType subjectType,
                                      VotingScope scope,
                                      Long scopeReferenceId,
                                      String title,
                                      Instant voteStartAt,
                                      Instant voteEndAt,
                                      Long proposedByUserId,
                                      java.math.BigDecimal partyRatioFloor) {
        Objects.requireNonNull(tenantId,         "tenantId 不能为空");
        Objects.requireNonNull(subjectType,      "subjectType 不能为空");
        Objects.requireNonNull(scope,            "scope 不能为空");
        Objects.requireNonNull(title,            "title 不能为空");
        Objects.requireNonNull(voteStartAt,      "voteStartAt 不能为空");
        Objects.requireNonNull(voteEndAt,        "voteEndAt 不能为空");
        Objects.requireNonNull(proposedByUserId, "proposedByUserId 不能为空");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title 不能为空白");
        }
        if (!voteEndAt.isAfter(voteStartAt)) {
            throw new IllegalArgumentException("voteEndAt 必须晚于 voteStartAt");
        }
        if (scope == VotingScope.BUILDING && scopeReferenceId == null) {
            throw new IllegalArgumentException("scope=BUILDING 时 scopeReferenceId 不能为空");
        }
        if (scope == VotingScope.UNIT) {
            throw new IllegalArgumentException("scope=UNIT 暂未实现，本期仅支持 COMMUNITY/BUILDING");
        }

        return VotingSubject.builder()
                .tenantId(tenantId)
                .title(title)
                .subjectType(subjectType)
                .scope(scope)
                .scopeReferenceId(scopeReferenceId)
                .status(SubjectStatus.DRAFT)
                .voteStartAt(voteStartAt)
                .voteEndAt(voteEndAt)
                .proposedByUserId(proposedByUserId)
                .partyRatioFloor(partyRatioFloor)
                .version(0L)
                .build();
    }

    /** DRAFT → PUBLISHED。public/admin 端均通过本方法变更状态。 */
    public static void publish(VotingSubject subject) {
        Objects.requireNonNull(subject, "subject 不能为空");
        ensureTransition(subject.getStatus(), SubjectStatus.PUBLISHED);
        subject.setStatus(SubjectStatus.PUBLISHED);
    }

    /** PUBLISHED → VOTING。仅 scheduler 调用；要求当前时间 ≥ vote_start_at。 */
    public static void openVoting(VotingSubject subject, Instant now) {
        Objects.requireNonNull(subject, "subject 不能为空");
        Objects.requireNonNull(now,     "now 不能为空");
        ensureTransition(subject.getStatus(), SubjectStatus.VOTING);
        if (subject.getVoteStartAt() == null || now.isBefore(subject.getVoteStartAt())) {
            throw new IllegalSubjectTransitionException(
                    "尚未到达 voteStartAt，无法开放投票 subjectId=" + subject.getSubjectId()
                            + " voteStartAt=" + subject.getVoteStartAt() + " now=" + now);
        }
        subject.setStatus(SubjectStatus.VOTING);
    }

    /** DRAFT 阶段发起者本人撤回。落 cancel_* 三件套。 */
    public static void cancelByProposer(VotingSubject subject, Long currentUserId, String reason, Instant now) {
        Objects.requireNonNull(subject,        "subject 不能为空");
        Objects.requireNonNull(currentUserId,  "currentUserId 不能为空");
        Objects.requireNonNull(now,            "now 不能为空");
        if (subject.getStatus() != SubjectStatus.DRAFT) {
            throw new IllegalSubjectTransitionException(
                    "DRAFT 阶段才允许发起者本人撤回 subjectId=" + subject.getSubjectId()
                            + " status=" + subject.getStatus());
        }
        if (!currentUserId.equals(subject.getProposedByUserId())) {
            throw new IllegalSubjectTransitionException(
                    "DRAFT 议题仅发起者本人可撤回 subjectId=" + subject.getSubjectId());
        }
        applyCancellation(subject, currentUserId, reason, now);
    }

    /** PUBLISHED 阶段政府强撤。 */
    public static void cancelByGovernment(VotingSubject subject, Long currentUserId, String reason, Instant now) {
        Objects.requireNonNull(subject,        "subject 不能为空");
        Objects.requireNonNull(currentUserId,  "currentUserId 不能为空");
        Objects.requireNonNull(now,            "now 不能为空");
        if (subject.getStatus() != SubjectStatus.PUBLISHED) {
            throw new IllegalSubjectTransitionException(
                    "强撤仅对 PUBLISHED 议题生效 subjectId=" + subject.getSubjectId()
                            + " status=" + subject.getStatus());
        }
        applyCancellation(subject, currentUserId, reason, now);
    }

    private static void applyCancellation(VotingSubject subject, Long currentUserId, String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("cancelReason 不能为空");
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("cancelReason 长度超过 500");
        }
        ensureTransition(subject.getStatus(), SubjectStatus.CANCELLED);
        subject.setStatus(SubjectStatus.CANCELLED);
        subject.setCancelledAt(now);
        subject.setCancelledByUserId(currentUserId);
        subject.setCancelReason(reason);
    }

    private static void ensureTransition(SubjectStatus from, SubjectStatus to) {
        EnumSet<SubjectStatus> allowed = TRANSITIONS.getOrDefault(from, EnumSet.noneOf(SubjectStatus.class));
        if (!allowed.contains(to)) {
            throw new IllegalSubjectTransitionException(
                    "非法状态跳转 from=" + from + " to=" + to);
        }
    }

    /** 状态机非法跳转抛出。application 层捕获并翻译为 {@code VotingApplicationException}。 */
    public static class IllegalSubjectTransitionException extends RuntimeException {
        public IllegalSubjectTransitionException(String message) {
            super(message);
        }
    }
}
