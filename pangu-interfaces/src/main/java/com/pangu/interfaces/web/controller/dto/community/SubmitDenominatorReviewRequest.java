package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.CommunitySettingsCommands;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SubmitDenominatorReviewRequest(
        @DecimalMin(value = "0.01") BigDecimal requestedTotalArea,
        @Min(1) Long requestedOwnerCount,
        @Min(1) Long requestedUnitCount,
        @NotBlank @Size(max = 500) String reason
) {
    public CommunitySettingsCommands.DenominatorReview toCommand() {
        return new CommunitySettingsCommands.DenominatorReview(
                requestedTotalArea, requestedOwnerCount, requestedUnitCount, reason);
    }
}
