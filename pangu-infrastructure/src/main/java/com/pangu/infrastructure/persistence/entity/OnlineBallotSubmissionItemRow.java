// 关联业务：映射一次线上整包提交中的逐事项统一票据关联。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class OnlineBallotSubmissionItemRow {
    private Long submissionItemId;
    private Long submissionId;
    private Long subjectId;
    private Integer choice;
    private Long unifiedBallotId;
    private String itemConfirmationHash;
}
