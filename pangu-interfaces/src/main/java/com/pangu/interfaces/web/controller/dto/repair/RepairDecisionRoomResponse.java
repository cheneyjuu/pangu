package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairDecisionRoom;

import java.math.BigDecimal;

public record RepairDecisionRoomResponse(Long roomId, String roomLabel, BigDecimal buildArea) {
    public static RepairDecisionRoomResponse from(RepairDecisionRoom room) {
        return new RepairDecisionRoomResponse(room.roomId(), room.roomLabel(), room.buildArea());
    }
}
