package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSupplierActivationInvitationRequest(
        @Size(max = 50) String contactName,
        @Pattern(regexp = "1[3-9][0-9]{9}") String contactPhone,
        @Min(1) @Max(168) Integer validHours
) {
}
