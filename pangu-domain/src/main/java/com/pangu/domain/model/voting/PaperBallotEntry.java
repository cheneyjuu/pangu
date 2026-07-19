// 关联业务：保存纸票逐事项录入版本及由另一名人员对照原件作出的复核结论。
package com.pangu.domain.model.voting;

import java.time.Instant;
import java.util.List;

/** 一版不可变纸票录入；退回时新增修订版本，不覆盖原记录。 */
public record PaperBallotEntry(
        Long entryId,
        Long paperBallotId,
        Long tenantId,
        Integer versionNumber,
        Status status,
        Long enteredByUserId,
        Instant enteredAt,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote,
        List<Item> items
) {

    public PaperBallotEntry {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public enum Status {
        PENDING_REVIEW,
        CONFIRMED,
        REJECTED
    }

    public enum Determination {
        VALID,
        INVALID
    }

    /** 无效原因是人工复核分类，系统不会仅凭原因代码自动判定票面无效。 */
    public enum InvalidReasonCode {
        BLANK,
        MULTIPLE_MARKS,
        UNREADABLE,
        WRONG_TEMPLATE,
        OTHER
    }

    public record Item(
            Long entryItemId,
            Long entryId,
            Long subjectId,
            Determination determination,
            VoteChoice choice,
            InvalidReasonCode invalidReasonCode,
            String invalidReasonDescription
    ) {
        public Item {
            if (subjectId == null || determination == null) {
                throw new IllegalArgumentException("纸票录入事项和判定不能为空");
            }
            invalidReasonDescription = trim(invalidReasonDescription);
            if (determination == Determination.VALID) {
                if (choice == null || invalidReasonCode != null || invalidReasonDescription != null) {
                    throw new IllegalArgumentException("有效纸票事项必须且只能填写表决选择");
                }
            } else if (choice != null || invalidReasonCode == null) {
                throw new IllegalArgumentException("无效纸票事项必须填写无效原因且不能填写表决选择");
            } else if (invalidReasonCode == InvalidReasonCode.OTHER && invalidReasonDescription == null) {
                throw new IllegalArgumentException("其他无效原因必须填写说明");
            }
        }

        private static String trim(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
