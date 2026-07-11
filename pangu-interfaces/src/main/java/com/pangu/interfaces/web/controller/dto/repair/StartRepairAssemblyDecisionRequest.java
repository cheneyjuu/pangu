package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StartRepairAssemblyDecisionRequest(
        @NotNull Long packageId,
        @Size(max = 500) String remark
) {
}
