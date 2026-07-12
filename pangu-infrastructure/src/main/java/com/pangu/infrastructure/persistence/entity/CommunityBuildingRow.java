package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class CommunityBuildingRow {
    private Long buildingId;
    private String buildingName;
    private Long unitCount;
    private Long roomCount;
}
