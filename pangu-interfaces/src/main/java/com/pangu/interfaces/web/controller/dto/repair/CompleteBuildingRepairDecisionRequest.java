// 关联业务：校验物业核验楼栋维修微信接龙原始逐户意见及证据的输入。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.CompleteBuildingRepairDecisionCommand;
import com.pangu.domain.model.repair.RepairVoteChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CompleteBuildingRepairDecisionRequest(
        @NotNull @Min(0) Integer expectedProcessVersion,
        @NotNull Long evidenceAttachmentId,
        @Valid @NotNull List<Entry> entries
) {
    public CompleteBuildingRepairDecisionCommand toCommand() {
        return new CompleteBuildingRepairDecisionCommand(
                expectedProcessVersion, evidenceAttachmentId,
                entries.stream().map(Entry::toCommand).toList());
    }

    public record Entry(
            @NotNull Long roomId,
            @NotNull RepairVoteChoice choice,
            @NotBlank @Size(max = 1000) String originalText
    ) {
        private CompleteBuildingRepairDecisionCommand.Entry toCommand() {
            return new CompleteBuildingRepairDecisionCommand.Entry(roomId, choice, originalText);
        }
    }
}
