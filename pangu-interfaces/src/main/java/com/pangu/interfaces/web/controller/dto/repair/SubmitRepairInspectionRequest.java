package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitRepairInspectionRequest(
        @Size(max = 16) String publicAreaScope,
        @Positive Long buildingId,
        @Positive Long roomId,
        @Size(max = 200) String locationText,
        @Size(max = 1000) String fieldSupplement,
        @NotBlank @Size(max = 2000) String surveySummary,
        @NotBlank @Size(max = 32) String riskLevel,
        @NotEmpty @Size(max = 3) List<@NotNull @Positive Long> evidenceImageAttachmentIds,
        @Positive Long evidenceVideoAttachmentId,
        @Size(max = 500) String remark
) {
}
