package com.pangu.domain.model.repair;

import java.math.BigDecimal;

/** 表决范围内的一个产权人参与单元，合并其在当前范围内的全部专有部分。 */
public record RepairDecisionRoom(
        Long roomId,
        String roomLabel,
        BigDecimal buildArea
) {
}
