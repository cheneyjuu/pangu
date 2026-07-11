package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OwnersAssemblyVoteRecordRow {
    private Long assemblyVoteId;
    private Long packageId;
    private Long subjectId;
    private Long voteId;
    private Long tenantId;
    private Long opid;
    private Long uid;
    private String voteChannel;
    private String packageHash;
    private String ballotFileHash;
    private String signatureHash;
    private Integer validFlag;
    private Long invalidatedByVoteId;
    private String invalidReason;
    private LocalDateTime createTime;
}
