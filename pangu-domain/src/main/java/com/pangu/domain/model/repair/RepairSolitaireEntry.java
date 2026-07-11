package com.pangu.domain.model.repair;

import java.math.BigDecimal;

/** 物业从微信接龙逐户核验后的结构化选择。 */
public record RepairSolitaireEntry(
        Long roomId,
        Long ownerUid,
        RepairVoteChoice choice,
        BigDecimal buildArea,
        String originalText
) {
}
