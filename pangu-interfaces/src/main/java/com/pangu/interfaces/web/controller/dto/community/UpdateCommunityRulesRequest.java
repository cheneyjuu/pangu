package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.CommunitySettingsCommands;
import jakarta.validation.constraints.Pattern;

public record UpdateCommunityRulesRequest(
        Boolean repairEstimateRequired,
        @Pattern(regexp = "ONLINE") String buildingRepairDefaultDecisionChannel
) {
    public CommunitySettingsCommands.Rules toCommand() {
        return new CommunitySettingsCommands.Rules(
                repairEstimateRequired, buildingRepairDefaultDecisionChannel);
    }
}
