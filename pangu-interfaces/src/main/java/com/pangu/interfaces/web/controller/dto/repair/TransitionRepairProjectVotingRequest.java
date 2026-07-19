// 关联业务：以表决关联版本防止重复开始或重复结算维修正式表决。
package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairProjectVotingService;
import jakarta.validation.constraints.NotNull;

public record TransitionRepairProjectVotingRequest(
        @NotNull Long expectedLinkVersion
) {
    public RepairProjectVotingService.TransitionCommand toCommand() {
        return new RepairProjectVotingService.TransitionCommand(expectedLinkVersion);
    }
}
