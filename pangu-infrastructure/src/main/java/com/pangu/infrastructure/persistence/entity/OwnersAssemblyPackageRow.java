package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnersAssemblyPackageRow {
    private Long packageId;
    private Long sessionId;
    private Long tenantId;
    private Long ruleSnapshotId;
    private Integer packageVersion;
    private String status;
    private String votingChannelPolicy;
    private Integer publicNoticeDays;
    private String announcementHash;
    private String attachmentManifestHash;
    private String ballotTemplateHash;
    private String electronicSealHash;
    private String packageHash;
    private LocalDateTime publicNoticeStartAt;
    private LocalDateTime publicNoticeEndAt;
    private LocalDateTime voteStartAt;
    private LocalDateTime voteEndAt;
    private Long lockedByUserId;
    private LocalDateTime lockedAt;
}
