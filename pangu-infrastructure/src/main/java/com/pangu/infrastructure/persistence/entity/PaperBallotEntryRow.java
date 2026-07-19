// 关联业务：映射纸票不可变录入版本及第二人复核结论。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class PaperBallotEntryRow {
    private Long entryId;
    private Long paperBallotId;
    private Long tenantId;
    private Integer versionNumber;
    private String status;
    private Long enteredByUserId;
    private Instant enteredAt;
    private Long reviewedByUserId;
    private Instant reviewedAt;
    private String reviewNote;
}
