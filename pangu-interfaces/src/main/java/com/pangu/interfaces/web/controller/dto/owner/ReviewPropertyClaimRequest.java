package com.pangu.interfaces.web.controller.dto.owner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewPropertyClaimRequest(
        @Size(max = 64, message = "reasonCode length must be <= 64")
        String reasonCode,
        @NotBlank(message = "reason must not be blank")
        @Size(max = 500, message = "reason length must be <= 500")
        String reason
) {
}
