// 关联业务：映射一版纸票录入中每个表决事项的有效选择或无效原因。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class PaperBallotEntryItemRow {
    private Long entryItemId;
    private Long entryId;
    private Long subjectId;
    private String determination;
    private Integer choice;
    private String invalidReasonCode;
    private String invalidReasonDescription;
}
