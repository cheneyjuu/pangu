// 关联业务：验证纸票录入中有效选择与无效原因互斥，避免把无效纸票误记为弃权或有效票。
package com.pangu.domain.model.voting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperBallotEntryTest {

    @Test
    void validEntryRequiresExactlyOneChoice() {
        assertDoesNotThrow(() -> new PaperBallotEntry.Item(
                null, null, 10L, PaperBallotEntry.Determination.VALID,
                VoteChoice.SUPPORT, null, null));

        assertThrows(IllegalArgumentException.class, () -> new PaperBallotEntry.Item(
                null, null, 10L, PaperBallotEntry.Determination.VALID,
                null, null, null));
    }

    @Test
    void invalidEntryCannotMasqueradeAsAChoice() {
        assertDoesNotThrow(() -> new PaperBallotEntry.Item(
                null, null, 10L, PaperBallotEntry.Determination.INVALID,
                null, PaperBallotEntry.InvalidReasonCode.BLANK, null));

        assertThrows(IllegalArgumentException.class, () -> new PaperBallotEntry.Item(
                null, null, 10L, PaperBallotEntry.Determination.INVALID,
                VoteChoice.ABSTAIN, PaperBallotEntry.InvalidReasonCode.BLANK, null));
        assertThrows(IllegalArgumentException.class, () -> new PaperBallotEntry.Item(
                null, null, 10L, PaperBallotEntry.Determination.INVALID,
                null, PaperBallotEntry.InvalidReasonCode.OTHER, "  "));
    }
}
