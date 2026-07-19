// 关联业务：向管理端展示一版纸票录入及其逐事项有效性判定和复核状态。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.PaperBallotEntry;

import java.time.Instant;
import java.util.List;

public record PaperBallotEntryResponse(
        Long entryId,
        Long paperBallotId,
        Integer versionNumber,
        String status,
        Long enteredByUserId,
        Instant enteredAt,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote,
        List<Item> items
) {
    public static PaperBallotEntryResponse from(PaperBallotEntry entry) {
        if (entry == null) {
            return null;
        }
        return new PaperBallotEntryResponse(
                entry.entryId(),
                entry.paperBallotId(),
                entry.versionNumber(),
                entry.status().name(),
                entry.enteredByUserId(),
                entry.enteredAt(),
                entry.reviewedByUserId(),
                entry.reviewedAt(),
                entry.reviewNote(),
                entry.items().stream().map(Item::from).toList());
    }

    public record Item(
            Long subjectId,
            String determination,
            String choice,
            String invalidReasonCode,
            String invalidReasonDescription
    ) {
        private static Item from(PaperBallotEntry.Item item) {
            return new Item(
                    item.subjectId(),
                    item.determination().name(),
                    item.choice() == null ? null : item.choice().name(),
                    item.invalidReasonCode() == null ? null : item.invalidReasonCode().name(),
                    item.invalidReasonDescription());
        }
    }
}
