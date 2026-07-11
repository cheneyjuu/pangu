package com.pangu.application.repair.command;

import java.util.List;

public record SetRepairAcceptanceScopeCommand(
        List<AffectedRoom> rooms,
        String remark
) {
    public record AffectedRoom(Long roomId, String affectedReason) {
    }
}
