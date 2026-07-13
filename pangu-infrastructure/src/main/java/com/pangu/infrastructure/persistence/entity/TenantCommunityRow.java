// 关联业务：映射小区运行时权威配置，包括已生效的物业管理模式。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class TenantCommunityRow {
    private Long tenantId;
    private String tenantCode;
    private String tenantShortCode;
    private String tenantName;
    private String propertyAreaName;
    private String propertyAreaCode;
    private String developerName;
    private Long developerAccountId;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String streetCode;
    private String streetName;
    private String communityCode;
    private String communityName;
    private String address;
    private Integer plannedHouseholdCount;
    private Integer deliveredHouseholdCount;
    private Integer registeredPropertyUnitCount;
    private Integer registeredVotingOwnerCount;
    private BigDecimal totalPlannedBuildingArea;
    private BigDecimal totalExclusiveArea;
    private BigDecimal registeredVotingTotalArea;
    private BigDecimal excludedParkingArea;
    private BigDecimal publicArea;
    private Integer buildingCount;
    private Integer unitCount;
    private Integer parkingSpaceCount;
    private BigDecimal plotRatio;
    private Integer ownersAssemblyEstablished;
    private Integer committeeEstablished;
    private String currentCommitteeTermName;
    private String transitionOrgType;
    private String transitionOrgStatus;
    private Long ruleConfigId;
    private String sharedOwnershipStrategy;
    private Integer repairEstimateRequired;
    private String buildingRepairDefaultDecisionChannel;
    private String propertyMode;
    private Integer fundManagedEnabled;
    private String financialControlConfigId;
    private Integer quarterlyDisclosureDeadlineDay;
    private Long statisticsVersion;
    private Instant statisticsUpdatedAt;
    private String governanceStatus;
    private String status;
    private Instant updateTime;
}
