package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOwnersAssemblySessionRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 32) String preparationMode
) {
}
