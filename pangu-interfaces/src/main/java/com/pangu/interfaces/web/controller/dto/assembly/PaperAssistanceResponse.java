// 关联业务：向业主返回纸质办理申请的本人状态。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.voting.OnlinePaperAssistanceRequest;

import java.time.Instant;

public record PaperAssistanceResponse(
        Long requestId,
        Long opid,
        String status,
        Instant requestedAt,
        Instant fulfilledAt,
        Instant withdrawnAt
) {
    public static PaperAssistanceResponse from(OnlinePaperAssistanceRequest source) {
        return new PaperAssistanceResponse(
                source.requestId(), source.opid(), source.status().name(), source.requestedAt(),
                source.fulfilledAt(), source.withdrawnAt());
    }
}
