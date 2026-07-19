// 关联业务：登记维修事项已回收的纸质表决票编号、对应专有部分和项目内原件。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairProjectVotingChannelService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RegisterRepairVotingPaperBallotRequest(
        @NotNull Long opid,
        @NotBlank String ballotNumber,
        @NotNull Long attachmentId,
        @NotNull Instant receivedAt
) {
    public RepairProjectVotingChannelService.RegisterBallotCommand toCommand() {
        return new RepairProjectVotingChannelService.RegisterBallotCommand(
                opid, ballotNumber, attachmentId, receivedAt);
    }
}
