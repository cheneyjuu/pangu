// 关联业务：映射业主线上整包确认提交及本人回执。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class OnlineBallotSubmissionRow {
    private Long submissionId;
    private Long packageId;
    private Long electorateItemId;
    private Long tenantId;
    private Long accountId;
    private Long uid;
    private Long opid;
    private String idempotencyKey;
    private String packageHash;
    private String choiceManifestHash;
    private String confirmationHash;
    private String status;
    private Instant submittedAt;
}
