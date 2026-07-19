// 关联业务：映射纸票复核确认后的计入、无效或重复材料逐事项结果。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class PaperBallotOutcomeRow {
    private Long outcomeId;
    private Long paperBallotId;
    private Long entryId;
    private Long subjectId;
    private String status;
    private Long unifiedBallotId;
    private Long conflictingBallotId;
    private String reason;
    private Instant finalizedAt;
}
