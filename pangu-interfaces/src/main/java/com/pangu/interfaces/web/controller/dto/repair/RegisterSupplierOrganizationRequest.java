package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterSupplierOrganizationRequest(
        @NotBlank @Size(max = 50) String legalName,
        @NotBlank @Pattern(regexp = "[0-9A-Za-z]{18}") String unifiedSocialCreditCode,
        @NotBlank @Size(max = 80) String contactName,
        @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String contactPhone
) {
}
