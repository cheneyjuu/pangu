package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.assembly.OwnersAssemblyPackage;
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
        Long clockSuspendedBySubjectId,
        OwnersAssemblyPackageInfo assemblyPackage
) {
    public static OwnerSubjectResponse from(VotingSubject s) {
        return from(s, null);
    }

    public static OwnerSubjectResponse from(VotingSubject s, OwnersAssemblyPackage assemblyPackage) {
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
                s.getClockSuspendedBySubjectId(),
                OwnersAssemblyPackageInfo.from(assemblyPackage));
    }

    public record OwnersAssemblyPackageInfo(
            Long packageId,
            String status,
            String votingChannelPolicy,
            boolean paperAllowed,
            boolean onlineAllowed,
            String ballotTemplateHash,
            String electronicSealHash,
            String packageHash,
            Instant publicNoticeStartAt,
            Instant publicNoticeEndAt,
            Instant voteStartAt,
            Instant voteEndAt
    ) {
        public static OwnersAssemblyPackageInfo from(OwnersAssemblyPackage p) {
            if (p == null) {
                return null;
            }
            return new OwnersAssemblyPackageInfo(
                    p.packageId(),
                    p.status(),
                    p.votingChannelPolicy(),
                    p.paperAllowed(),
                    p.onlineAllowed(),
                    p.ballotTemplateHash(),
                    p.electronicSealHash(),
                    p.packageHash(),
                    p.publicNoticeStartAt(),
                    p.publicNoticeEndAt(),
                    p.voteStartAt(),
                    p.voteEndAt());
        }
    }
}
