// 关联业务：保存纸票复核确认后每个表决事项的计入、无效或重复材料最终结果。
package com.pangu.domain.model.voting;

import java.time.Instant;

/** 纸票事项最终处理结果；只有 COUNTED 关联统一有效票。 */
public record PaperBallotOutcome(
        Long outcomeId,
        Long paperBallotId,
        Long entryId,
        Long subjectId,
        Status status,
        Long unifiedBallotId,
        Long conflictingBallotId,
        String reason,
        Instant finalizedAt
) {

    public enum Status {
        COUNTED,
        INVALID,
        DUPLICATE
    }
}
