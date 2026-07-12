package com.pangu.domain.model.repair;

import java.math.BigDecimal;

/** C 端业主可参与的楼栋维修在线表决。 */
public record RepairOwnerLocalDecision(
        Long decisionId,
        Long workOrderId,
        String orderNo,
        String title,
        String locationText,
        String surveySummary,
        String scopeLabel,
        Long opid,
        Long roomId,
        String roomName,
        BigDecimal buildArea,
        String supplierName,
        BigDecimal quoteAmount,
        String quoteSummary,
        Long quoteAttachmentId,
        RepairVoteChoice myChoice
) {
}
