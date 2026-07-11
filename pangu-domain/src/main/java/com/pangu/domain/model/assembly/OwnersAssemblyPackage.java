package com.pangu.domain.model.assembly;

import java.time.Instant;

/** 业主大会表决包：公告、附件、选票模板、公章与版本哈希的锁定单元。 */
public record OwnersAssemblyPackage(
        Long packageId,
        Long sessionId,
        Long tenantId,
        Integer packageVersion,
        String status,
        String votingChannelPolicy,
        Integer publicNoticeDays,
        String announcementHash,
        String attachmentManifestHash,
        String ballotTemplateHash,
        String electronicSealHash,
        String packageHash,
        Instant publicNoticeStartAt,
        Instant publicNoticeEndAt,
        Instant voteStartAt,
        Instant voteEndAt,
        Long lockedByUserId,
        Instant lockedAt
) {
    public boolean paperAllowed() {
        return "PAPER_ONLY".equals(votingChannelPolicy) || "PAPER_AND_ONLINE".equals(votingChannelPolicy);
    }

    public boolean onlineAllowed() {
        return "ONLINE_ONLY".equals(votingChannelPolicy) || "PAPER_AND_ONLINE".equals(votingChannelPolicy);
    }
}
