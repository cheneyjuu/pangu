package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SetRepairAcceptanceScopeRequest(
        @NotEmpty List<@Valid AffectedRoom> rooms,
        @Size(max = 500) String remark
) {
    public record AffectedRoom(
            @NotNull Long roomId,
            @Size(max = 500) String affectedReason
    ) {
    }
}
