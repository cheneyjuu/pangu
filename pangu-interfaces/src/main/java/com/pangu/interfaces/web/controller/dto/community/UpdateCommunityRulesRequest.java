package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.CommunitySettingsCommands;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCommunityRulesRequest(
        Long ruleConfigId,
        @Pattern(regexp = "REPRESENTATIVE_ONLY|PROPORTIONAL_SPLIT") String sharedOwnershipStrategy,
        Boolean repairEstimateRequired,
        @Pattern(regexp = "ONLINE|WECHAT") String buildingRepairDefaultDecisionChannel,
        Boolean fundManagedEnabled,
        @Size(max = 64) String financialControlConfigId,
        @Min(1) @Max(31) Integer quarterlyDisclosureDeadlineDay
) {
    public CommunitySettingsCommands.Rules toCommand() {
        return new CommunitySettingsCommands.Rules(
                ruleConfigId, sharedOwnershipStrategy, repairEstimateRequired,
                buildingRepairDefaultDecisionChannel, fundManagedEnabled,
                financialControlConfigId, quarterlyDisclosureDeadlineDay);
    }
}
