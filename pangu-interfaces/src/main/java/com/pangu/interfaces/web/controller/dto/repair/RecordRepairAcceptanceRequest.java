package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordRepairAcceptanceRequest(
        Long roomId,
        @Size(max = 32) String participantType,
        @NotBlank @Size(max = 120) String participantName,
        @NotBlank @Size(max = 32) String conclusion,
        @Size(max = 1000) String opinion,
        @Size(max = 128) String signatureHash,
        @Size(max = 128) String evidenceHash,
        @Size(max = 500) String remark
) {
}
