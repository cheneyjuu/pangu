package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.CommunitySettingsCommands;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDenominatorRequest(
        @NotNull Boolean approved,
        @Size(max = 500) String reviewComment
) {
    public CommunitySettingsCommands.ReviewDecision toCommand() {
        return new CommunitySettingsCommands.ReviewDecision(Boolean.TRUE.equals(approved), reviewComment);
    }
}
