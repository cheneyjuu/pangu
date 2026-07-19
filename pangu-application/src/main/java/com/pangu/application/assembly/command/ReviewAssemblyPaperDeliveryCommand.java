// 关联业务：由管理端工作身份核对业主大会纸质材料的逐户送达登记。
package com.pangu.application.assembly.command;

import com.pangu.application.voting.PaperVotingService;

import java.time.Instant;

public record ReviewAssemblyPaperDeliveryCommand(
        Long sessionId,
        Long paperDeliveryId,
        Long tenantId,
        PaperVotingService.ReviewDecision decision,
        String reviewNote,
        Long reviewedByUserId,
        Instant reviewedAt
) {
}
