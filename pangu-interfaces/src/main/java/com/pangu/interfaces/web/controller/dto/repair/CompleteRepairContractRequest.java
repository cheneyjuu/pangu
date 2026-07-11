package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CompleteRepairContractRequest(
        @NotEmpty List<@Valid Signature> signatures,
        @NotBlank @Size(max = 128) String finalContractFileHash,
        @Size(max = 500) String remark
) {
    public record Signature(
            @NotBlank @Size(max = 32) String partyType,
            @NotBlank @Size(max = 120) String signerName,
            Long signerUserId,
            @NotBlank @Size(max = 24) String signatureMethod,
            @NotBlank @Size(max = 128) String signatureFileHash,
            @NotNull LocalDateTime signedAt
    ) {
    }
}
