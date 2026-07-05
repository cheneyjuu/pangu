package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CommunityLedgerStatsRow {
    private BigDecimal totalArea;
    private Long ownerCount;
    private Long unitCount;
    private Long buildingCount;
}
