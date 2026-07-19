// 关联业务：接收纸质送达或纸票录入的人工核对结论；退回时必须同时说明原因。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.application.voting.PaperVotingService;
import jakarta.validation.constraints.NotNull;

public record ReviewPaperVotingRecordRequest(
        @NotNull PaperVotingService.ReviewDecision decision,
        String reviewNote
) {
}
