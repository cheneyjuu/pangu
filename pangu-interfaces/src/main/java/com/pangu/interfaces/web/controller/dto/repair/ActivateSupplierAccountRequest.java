package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ActivateSupplierAccountRequest(
        @NotNull Long invitationId,
        @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
        @NotBlank @Size(max = 12) String smsCode,
        @NotBlank @Size(max = 50) String operatorName
) {
}
