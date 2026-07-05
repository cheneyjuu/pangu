package com.pangu.domain.model.community;

import java.math.BigDecimal;

/**
 * 从业主房产台账实时聚合出的计票基数参考值。
 */
public record CommunityLedgerStats(
        BigDecimal totalArea,
        long ownerCount,
        long unitCount,
        long buildingCount
) {
}
