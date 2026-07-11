package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SealRepairGovernanceRequest(
        @Size(max = 32) String sealType,
        @NotBlank @Size(max = 128) String sealedFileHash,
        @Size(max = 500) String remark
) {
}
