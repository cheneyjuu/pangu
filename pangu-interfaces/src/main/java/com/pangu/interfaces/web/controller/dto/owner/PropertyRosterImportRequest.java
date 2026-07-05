package com.pangu.interfaces.web.controller.dto.owner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record PropertyRosterImportRequest(
        Long tenantId,
        @NotEmpty(message = "rows must not be empty")
        @Size(max = 2000, message = "rows size must be <= 2000")
        List<@Valid Row> rows
) {
    public record Row(
            Long tenantId,
            @NotBlank(message = "buildingName must not be blank")
            @Size(max = 64)
            String buildingName,
            @NotBlank(message = "unitName must not be blank")
            @Size(max = 64)
            String unitName,
            @NotBlank(message = "roomName must not be blank")
            @Size(max = 64)
            String roomName,
            BigDecimal buildArea,
            @NotBlank(message = "registeredOwnerName must not be blank")
            @Size(max = 64)
            String registeredOwnerName,
            @NotBlank(message = "registeredOwnerPhone must not be blank")
            @Pattern(regexp = "^1\\d{10}$", message = "registeredOwnerPhone must be a mainland China mobile number")
            String registeredOwnerPhone
    ) {
    }
}
