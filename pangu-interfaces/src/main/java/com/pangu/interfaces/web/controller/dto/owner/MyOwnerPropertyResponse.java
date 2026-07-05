package com.pangu.interfaces.web.controller.dto.owner;

import com.pangu.domain.model.asset.OwnerPropertyDetail;

import java.math.BigDecimal;

public record MyOwnerPropertyResponse(
        Long opid,
        Long uid,
        Long tenantId,
        String communityName,
        Long buildingId,
        String buildingName,
        String unitName,
        Long roomId,
        String roomName,
        BigDecimal buildAreaSqm,
        int isJointOwnership,
        int isVotingDelegate,
        Integer accountStatus,
        String verifyType,
        String verifyStatus
) {
    public static MyOwnerPropertyResponse from(OwnerPropertyDetail d, Long uid) {
        return new MyOwnerPropertyResponse(
                d.opid(),
                uid,
                d.tenantId(),
                d.communityName(),
                d.buildingId(),
                d.buildingName(),
                d.unitName(),
                d.roomId(),
                d.roomName(),
                d.buildArea(),
                d.jointOwnership() ? 1 : 0,
                d.votingDelegate() ? 1 : 0,
                d.accountStatus(),
                d.verifyType(),
                d.verifyStatus());
    }
}
