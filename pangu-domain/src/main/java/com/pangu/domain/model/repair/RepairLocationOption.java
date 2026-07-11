package com.pangu.domain.model.repair;

public record RepairLocationOption(
        Long tenantId,
        String communityName,
        Long buildingId,
        String buildingName,
        String unitName,
        Long roomId,
        String roomName
) {
}
