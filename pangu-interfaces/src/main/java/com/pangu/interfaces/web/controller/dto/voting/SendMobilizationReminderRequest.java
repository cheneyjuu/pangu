package com.pangu.interfaces.web.controller.dto.voting;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMobilizationReminderRequest(
        @NotNull Long buildingId,
        @Size(max = 500) String message
) {
}
