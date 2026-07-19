// 关联业务：校验维修授权提案本次正式表决方式和时间窗口。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairProjectVotingService;
import com.pangu.domain.model.voting.VotingExecutionPackage;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PrepareRepairProjectVotingRequest(
        @NotNull Integer expectedProjectVersion,
        @NotNull VotingExecutionPackage.CollectionMode collectionMode,
        @NotNull Long paperBallotTemplateAttachmentId,
        @NotNull Instant voteStartAt,
        @NotNull Instant voteEndAt
) {
    public RepairProjectVotingService.PrepareCommand toCommand() {
        return new RepairProjectVotingService.PrepareCommand(
                expectedProjectVersion, collectionMode, paperBallotTemplateAttachmentId, voteStartAt, voteEndAt);
    }
}
