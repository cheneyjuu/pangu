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
}
