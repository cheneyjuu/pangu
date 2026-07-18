// 关联业务：校验 C 端业主提交楼栋维修在线表决的房屋代表项与选择。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.command.SubmitOwnerRepairProjectDecisionVoteCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitOwnerRepairProjectDecisionVoteRequest(
        @NotNull Long roomId,
        @NotBlank String choice
) {
    public SubmitOwnerRepairProjectDecisionVoteCommand toCommand() {
        return new SubmitOwnerRepairProjectDecisionVoteCommand(roomId, choice);
    }
}
