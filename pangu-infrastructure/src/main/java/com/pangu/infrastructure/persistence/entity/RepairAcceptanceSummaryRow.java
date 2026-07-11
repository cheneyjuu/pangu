package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class RepairAcceptanceSummaryRow {
    private Integer affectedRoomCount;
    private Integer passedAffectedRoomCount;
    private Integer rectificationCount;
    private Integer unreachableCount;
    private Boolean ownerRepresentativePassed;
    private Boolean propertyRepresentativePassed;
}
