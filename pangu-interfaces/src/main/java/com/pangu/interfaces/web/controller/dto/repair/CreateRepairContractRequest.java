package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateRepairContractRequest(
        Long supplierDeptId,
        @Size(max = 120) String supplierName,
        @NotNull @DecimalMin("0.00") BigDecimal contractAmount,
        @NotBlank @Size(max = 128) String repairScopeHash,
        @NotBlank @Size(max = 64) String fundSource,
        @NotBlank @Size(max = 24) String signingMethod,
        @NotBlank @Size(max = 128) String contractFileHash,
        @Size(max = 500) String remark
) {
}
