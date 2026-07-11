package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyPackage;

import java.time.Instant;

public record OwnersAssemblyPackageResponse(
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
    public static OwnersAssemblyPackageResponse from(OwnersAssemblyPackage ballotPackage) {
        return new OwnersAssemblyPackageResponse(
                ballotPackage.packageId(),
                ballotPackage.sessionId(),
                ballotPackage.tenantId(),
                ballotPackage.packageVersion(),
                ballotPackage.status(),
                ballotPackage.votingChannelPolicy(),
                ballotPackage.publicNoticeDays(),
                ballotPackage.announcementHash(),
                ballotPackage.attachmentManifestHash(),
                ballotPackage.ballotTemplateHash(),
                ballotPackage.electronicSealHash(),
                ballotPackage.packageHash(),
                ballotPackage.publicNoticeStartAt(),
                ballotPackage.publicNoticeEndAt(),
                ballotPackage.voteStartAt(),
                ballotPackage.voteEndAt(),
                ballotPackage.lockedByUserId(),
                ballotPackage.lockedAt());
    }
}
