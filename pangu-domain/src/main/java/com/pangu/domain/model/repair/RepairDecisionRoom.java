package com.pangu.domain.model.repair;

import java.math.BigDecimal;

/** 接龙范围内的一套房屋及法定面积分母。 */
public record RepairDecisionRoom(
        Long roomId,
        BigDecimal buildArea
) {
}
