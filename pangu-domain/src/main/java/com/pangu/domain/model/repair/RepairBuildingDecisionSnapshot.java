package com.pangu.domain.model.repair;

import java.math.BigDecimal;

/** 单楼栋/单元维修接龙的分母快照。 */
public record RepairBuildingDecisionSnapshot(
        int totalOwnerCount,
        BigDecimal totalArea
) {
}
