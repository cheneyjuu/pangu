package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class RepairLocationOptionRow {
    private Long tenantId;
    private String communityName;
    private Long buildingId;
    private String buildingName;
    private String unitName;
    private Long roomId;
    private String roomName;
}
