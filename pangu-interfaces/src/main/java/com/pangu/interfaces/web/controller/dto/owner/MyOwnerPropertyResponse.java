package com.pangu.interfaces.web.controller.dto.owner;

import com.pangu.domain.model.asset.OwnerPropertyDetail;

import java.math.BigDecimal;

public record MyOwnerPropertyResponse(
        Long opid,
        Long uid,
        Long tenantId,
        Long buildingId,
        Long roomId,
        BigDecimal buildAreaSqm,
        int isJointOwnership,
        int isVotingDelegate,
        Integer accountStatus
) {
    public static MyOwnerPropertyResponse from(OwnerPropertyDetail d, Long uid, Long tenantId) {
        return new MyOwnerPropertyResponse(
                d.opid(),
                uid,
                tenantId,
                d.buildingId(),
                d.roomId(),
                d.buildArea(),
                0,
                d.votingDelegate() ? 1 : 0,
                d.accountStatus());
    }
}
