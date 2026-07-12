package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairOwnerLocalDecision;

import java.math.BigDecimal;

public record RepairOwnerLocalDecisionResponse(
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
        String myChoice
) {
    public static RepairOwnerLocalDecisionResponse from(RepairOwnerLocalDecision decision) {
        return new RepairOwnerLocalDecisionResponse(
                decision.decisionId(), decision.workOrderId(), decision.orderNo(), decision.title(),
                decision.locationText(), decision.surveySummary(), decision.scopeLabel(), decision.opid(),
                decision.roomId(), decision.roomName(), decision.buildArea(), decision.supplierName(),
                decision.quoteAmount(), decision.quoteSummary(), decision.quoteAttachmentId(),
                decision.myChoice() == null ? null : decision.myChoice().name());
    }
}
