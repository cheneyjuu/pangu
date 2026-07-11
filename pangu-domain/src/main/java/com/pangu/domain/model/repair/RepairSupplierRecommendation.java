package com.pangu.domain.model.repair;

import java.time.LocalDateTime;

/** 物业推荐供应商结果。 */
public record RepairSupplierRecommendation(
        Long recommendationId,
        Long workOrderId,
        Long tenantId,
        Long quoteId,
        Long recommendedByUserId,
        String recommendationReason,
        boolean singleSource,
        String singleSourceReason,
        RepairSupplierSelectionMethod selectionMethod,
        String insufficientQuoteReason,
        Long frameworkRelationId,
        LocalDateTime createTime
) {
}
