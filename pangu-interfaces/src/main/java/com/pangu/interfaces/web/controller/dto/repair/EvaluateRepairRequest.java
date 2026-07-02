package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvaluateRepairRequest(
        @NotNull @Min(1) @Max(5) Integer satisfactionScore,
        @Size(max = 500) String comment
) {
}
