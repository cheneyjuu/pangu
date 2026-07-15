// 关联业务：核验楼栋维修微信接龙逐户原文和截图，并按人数、面积双维度结算。
package com.pangu.application.repair.command;

import com.pangu.domain.model.repair.RepairVoteChoice;

import java.util.List;

public record CompleteBuildingRepairDecisionCommand(
        Integer expectedProcessVersion,
        Long evidenceAttachmentId,
        List<Entry> entries
) {
    public CompleteBuildingRepairDecisionCommand {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(
            Long roomId,
            RepairVoteChoice choice,
            String originalText
    ) {
    }
}
