package com.pangu.interfaces.web.controller.dto.community;

import com.pangu.application.community.CommunitySettingsCommands;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateCommunityAssetLedgerRequest(
        @Size(max = 128) String propertyAreaName,
        @Size(max = 64) String propertyAreaCode,
        @Size(max = 128) String developerName,
        Long developerAccountId,
        @Min(0) Integer plannedHouseholdCount,
        @Min(0) Integer deliveredHouseholdCount,
        @Min(0) Integer registeredPropertyUnitCount,
        @DecimalMin(value = "0.00") BigDecimal totalPlannedBuildingArea,
        @DecimalMin(value = "0.00") BigDecimal totalExclusiveArea,
        @DecimalMin(value = "0.00") BigDecimal registeredVotingTotalArea,
        @DecimalMin(value = "0.00") BigDecimal excludedParkingArea,
        @DecimalMin(value = "0.00") BigDecimal publicArea,
        @Min(0) Integer buildingCount,
        @Min(0) Integer unitCount,
        @Min(0) Integer parkingSpaceCount,
        @DecimalMin(value = "0.00") BigDecimal plotRatio
) {
    public CommunitySettingsCommands.AssetLedger toCommand() {
        return new CommunitySettingsCommands.AssetLedger(
                propertyAreaName, propertyAreaCode, developerName, developerAccountId,
                plannedHouseholdCount, deliveredHouseholdCount, registeredPropertyUnitCount,
                totalPlannedBuildingArea, totalExclusiveArea, registeredVotingTotalArea,
                excludedParkingArea, publicArea, buildingCount, unitCount, parkingSpaceCount, plotRatio);
    }
}
