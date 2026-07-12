package com.pangu.domain.model.repair;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 单楼栋维修接龙决策。 */
public record RepairLocalDecision(
        Long decisionId,
        Long workOrderId,
        Long tenantId,
        Long buildingId,
        RepairLocalDecisionScopeType scopeType,
        RepairLocalDecisionChannel decisionChannel,
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
        boolean printedAndAttached,
        String result,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal THREE = new BigDecimal("3");

    public boolean currentThresholdPassed() {
        return participatedOwnerCount != null
                && participatedArea != null
                && agreeOwnerCount != null
                && agreeArea != null
                && passesThreshold(
                        participatedOwnerCount, participatedArea,
                        agreeOwnerCount, agreeArea,
                        totalOwnerCount, totalArea);
    }

    public static boolean passesThreshold(int participatedOwnerCount,
                                          BigDecimal participatedArea,
                                          int agreeOwnerCount,
                                          BigDecimal agreeArea,
                                          int totalOwnerCount,
                                          BigDecimal totalArea) {
        boolean quorum = participatedOwnerCount * 3 >= totalOwnerCount * 2
                && participatedArea.multiply(THREE).compareTo(totalArea.multiply(TWO)) >= 0;
        boolean majority = agreeOwnerCount * 2 > participatedOwnerCount
                && agreeArea.multiply(TWO).compareTo(participatedArea) > 0;
        return quorum && majority;
    }

    public RepairLocalDecision withResult(String nextResult) {
        return new RepairLocalDecision(
                decisionId, workOrderId, tenantId, buildingId, scopeType, decisionChannel,
                unitName, scopeLabel, totalOwnerCount, totalArea, participatedOwnerCount,
                participatedArea, agreeOwnerCount, agreeArea, disagreeOwnerCount, disagreeArea,
                abstainOwnerCount, abstainArea, invalidOwnerCount, invalidArea,
                evidenceAttachmentHash, printedAndAttached, nextResult, createTime, updateTime);
    }
}
