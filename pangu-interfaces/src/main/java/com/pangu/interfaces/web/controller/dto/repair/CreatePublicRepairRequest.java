package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePublicRepairRequest(
        @Size(max = 16) String publicAreaScope,
        Long buildingId,
        @Size(max = 200) String locationText,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @Size(max = 64) String category,
        @Size(max = 2000) String evidenceText
) {
}
