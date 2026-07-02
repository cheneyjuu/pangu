package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Size;

public record CorrectRepairLocationRequest(
        Long buildingId,
        Long roomId,
        @Size(max = 200) String locationText,
        @Size(max = 500) String reason
) {
}
