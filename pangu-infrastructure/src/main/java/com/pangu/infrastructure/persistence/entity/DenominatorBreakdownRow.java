package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DenominatorBreakdownRow {
    private String assetType;
    private Long registeredUnitCount;
    private Long votingOwnerCount;
    private BigDecimal buildingArea;
    private BigDecimal baseRatio;
    private String operationStatus;
}
