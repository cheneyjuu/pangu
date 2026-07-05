package com.pangu.application.community;

import java.math.BigDecimal;

/**
 * 社区设置写侧命令集合。
 */
public final class CommunitySettingsCommands {

    private CommunitySettingsCommands() {
    }

    public record Organization(
            String tenantName,
            String provinceCode,
            String provinceName,
            String cityCode,
            String cityName,
            String districtCode,
            String districtName,
            String streetCode,
            String streetName,
            String communityCode,
            String communityName,
            String address,
            Boolean ownersAssemblyEstablished,
            Boolean committeeEstablished,
            String currentCommitteeTermName,
            String transitionOrgType,
            String transitionOrgStatus
    ) {
    }

    public record AssetLedger(
            String propertyAreaName,
            String propertyAreaCode,
            String developerName,
            Long developerAccountId,
            Integer plannedHouseholdCount,
            Integer deliveredHouseholdCount,
            Integer registeredPropertyUnitCount,
            BigDecimal totalPlannedBuildingArea,
            BigDecimal totalExclusiveArea,
            BigDecimal registeredVotingTotalArea,
            BigDecimal excludedParkingArea,
            BigDecimal publicArea,
            Integer buildingCount,
            Integer unitCount,
            Integer parkingSpaceCount,
            BigDecimal plotRatio
    ) {
    }

    public record Rules(
            Long ruleConfigId,
            String sharedOwnershipStrategy,
            Boolean fundManagedEnabled,
            String financialControlConfigId,
            Integer quarterlyDisclosureDeadlineDay
    ) {
    }

    public record DenominatorReview(
            BigDecimal requestedTotalArea,
            Long requestedOwnerCount,
            Long requestedUnitCount,
            String reason
    ) {
    }

    public record ReviewDecision(
            boolean approved,
            String reviewComment
    ) {
    }
}
