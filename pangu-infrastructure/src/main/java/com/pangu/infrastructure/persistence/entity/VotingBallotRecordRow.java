// 关联业务：映射纸质和线上统一票据台账与结构化有效票的对应关系。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class VotingBallotRecordRow {
    private Long ballotId;
    private Long packageId;
    private Long subjectId;
    private Long voteId;
    private Long electorateItemId;
    private Long tenantId;
    private Long representativeOpid;
    private Long representativeUid;
    private Integer voteChannel;
    private String packageHash;
    private String ballotFileHash;
    private String signatureHash;
    private Long recordedByUserId;
    private Instant castAt;
}
