package com.pangu.application.repair.command;

import java.util.List;

public record CompleteRepairLocalDecisionCommand(
        List<Entry> entries,
        String evidenceAttachmentHash,
        String remark
) {
    public record Entry(
            Long roomId,
            Long ownerUid,
            String choice,
            String originalText
    ) {
    }
}
