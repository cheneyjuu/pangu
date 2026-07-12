package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairLocalDecision;

import java.math.BigDecimal;

public record RepairLocalDecisionResponse(
        Long decisionId,
        String decisionChannel,
        String scopeType,
        String unitName,
        String scopeLabel,
        int totalOwnerCount,
        BigDecimal totalArea,
        Integer participatedOwnerCount,
        BigDecimal participatedArea,
        Integer agreeOwnerCount,
        BigDecimal agreeArea,
        Integer disagreeOwnerCount,
        BigDecimal disagreeArea,
        Integer abstainOwnerCount,
        BigDecimal abstainArea,
        Integer invalidOwnerCount,
        BigDecimal invalidArea,
        String evidenceAttachmentHash,
        boolean currentThresholdPassed,
        String result
) {
    public static RepairLocalDecisionResponse from(RepairLocalDecision decision) {
        boolean collectingOnline = decision.decisionChannel().name().equals("ONLINE")
                && (decision.result().equals("COLLECTING") || decision.result().equals("PAUSED"));
        return new RepairLocalDecisionResponse(
                decision.decisionId(), decision.decisionChannel().name(), decision.scopeType().name(),
                decision.unitName(), decision.scopeLabel(), decision.totalOwnerCount(),
                decision.totalArea(), decision.participatedOwnerCount(), decision.participatedArea(),
                collectingOnline ? null : decision.agreeOwnerCount(),
                collectingOnline ? null : decision.agreeArea(),
                collectingOnline ? null : decision.disagreeOwnerCount(),
                collectingOnline ? null : decision.disagreeArea(),
                collectingOnline ? null : decision.abstainOwnerCount(),
                collectingOnline ? null : decision.abstainArea(),
                collectingOnline ? null : decision.invalidOwnerCount(),
                collectingOnline ? null : decision.invalidArea(),
                decision.evidenceAttachmentHash(), decision.currentThresholdPassed(), decision.result());
    }
}
