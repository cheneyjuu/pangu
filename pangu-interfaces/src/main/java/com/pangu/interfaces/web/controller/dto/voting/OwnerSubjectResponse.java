package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;

import java.time.Instant;

/**
 * C 端业主可见议题视图（M3-2）。
 *
 * <p>故意不暴露 {@code partyRatioFloor / version / cancelled_*}：
 * 投票期间业主端只关心标题、类型、状态、起止时间；撤回审计仅在 B/G 端可见。
 * 也不返回当前票数，防止从众心理（结算后才暴露 SettleResult 中的票数）。
 */
public record OwnerSubjectResponse(
        Long subjectId,
        String title,
        String content,
        SubjectType subjectType,
        SubjectStatus status,
        VotingScope scope,
        Long scopeReferenceId,
        Instant voteStartAt,
        Instant voteEndAt,
        Instant clockSuspendedAt,
        Long clockSuspendedBySubjectId
) {
    public static OwnerSubjectResponse from(VotingSubject s) {
        return new OwnerSubjectResponse(
                s.getSubjectId(),
                s.getTitle(),
                s.getContent(),
                s.getSubjectType(),
                s.getStatus(),
                s.getScope(),
                s.getScopeReferenceId(),
                s.getVoteStartAt(),
                s.getVoteEndAt(),
                s.getClockSuspendedAt(),
                s.getClockSuspendedBySubjectId());
    }
}
