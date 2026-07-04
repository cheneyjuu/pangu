package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.SubjectStatus;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.model.voting.VotingScope;
import com.pangu.domain.model.voting.VotingSubject;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * B/G 管理端议题视图（M3-2）。包含撤回审计字段，用于运营核查。
 */
public record AdminSubjectResponse(
        Long subjectId,
        Long tenantId,
        String title,
        String content,
        SubjectType subjectType,
        SubjectStatus status,
        VotingScope scope,
        Long scopeReferenceId,
        BigDecimal partyRatioFloor,
        Instant voteStartAt,
        Instant voteEndAt,
        Instant clockSuspendedAt,
        Long clockSuspendedBySubjectId,
        Long proposedByUserId,
        Instant cancelledAt,
        Long cancelledByUserId,
        String cancelReason,
        long version
) {
    public static AdminSubjectResponse from(VotingSubject s) {
        return new AdminSubjectResponse(
                s.getSubjectId(),
                s.getTenantId(),
                s.getTitle(),
                s.getContent(),
                s.getSubjectType(),
                s.getStatus(),
                s.getScope(),
                s.getScopeReferenceId(),
                s.getPartyRatioFloor(),
                s.getVoteStartAt(),
                s.getVoteEndAt(),
                s.getClockSuspendedAt(),
                s.getClockSuspendedBySubjectId(),
                s.getProposedByUserId(),
                s.getCancelledAt(),
                s.getCancelledByUserId(),
                s.getCancelReason(),
                s.getVersion());
    }
}
