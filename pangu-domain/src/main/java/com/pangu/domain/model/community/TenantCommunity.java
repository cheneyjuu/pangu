package com.pangu.domain.model.community;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 租户社区法权配置当前版本。
 */
public record TenantCommunity(
        Long tenantId,
        String tenantCode,
        String tenantShortCode,
        String tenantName,
        String propertyAreaName,
        String propertyAreaCode,
        String developerName,
        Long developerAccountId,
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
        int plannedHouseholdCount,
        int deliveredHouseholdCount,
        int registeredPropertyUnitCount,
        int registeredVotingOwnerCount,
        BigDecimal totalPlannedBuildingArea,
        BigDecimal totalExclusiveArea,
        BigDecimal registeredVotingTotalArea,
        BigDecimal excludedParkingArea,
        BigDecimal publicArea,
        int buildingCount,
        int unitCount,
        int parkingSpaceCount,
        BigDecimal plotRatio,
        boolean ownersAssemblyEstablished,
        boolean committeeEstablished,
        String currentCommitteeTermName,
        String transitionOrgType,
        String transitionOrgStatus,
        Long ruleConfigId,
        String sharedOwnershipStrategy,
        boolean fundManagedEnabled,
        String financialControlConfigId,
        int quarterlyDisclosureDeadlineDay,
        long statisticsVersion,
        Instant statisticsUpdatedAt,
        String governanceStatus,
        String status,
        Instant updateTime
) {
}
