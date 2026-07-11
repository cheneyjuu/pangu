package com.pangu.interfaces.web.controller.dto.repair;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CorrectRepairLocationRequest(
        Long buildingId,
        Long roomId,
        @Size(max = 200) String locationText,
        @Size(max = 500) String reason,
        @Size(max = 1000) String fieldSupplement,
        @Size(max = 3) List<@Positive Long> evidenceImageAttachmentIds
) {
}
