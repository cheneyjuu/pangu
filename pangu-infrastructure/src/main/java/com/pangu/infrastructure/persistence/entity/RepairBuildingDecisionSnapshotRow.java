package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RepairBuildingDecisionSnapshotRow {
    private Integer totalOwnerCount;
    private BigDecimal totalArea;
}
