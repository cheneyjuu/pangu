package com.pangu.domain.model.community;

import java.math.BigDecimal;

/**
 * 法定计票基数分类展示行。
 */
public record DenominatorBreakdown(
        String assetType,
        long registeredUnitCount,
        long votingOwnerCount,
        BigDecimal buildingArea,
        BigDecimal baseRatio,
        String operationStatus
) {
}
