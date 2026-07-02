package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePrivateRepairRequest(
        @NotNull Long opid,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @Size(max = 64) String category,
        @Size(max = 2000) String evidenceText
) {
}
