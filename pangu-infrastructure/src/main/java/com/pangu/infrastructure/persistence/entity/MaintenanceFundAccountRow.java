package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MaintenanceFundAccountRow {

    private Long accountId;
    private Long tenantId;
    private BigDecimal totalBalance;
    private BigDecimal frozenBalance;
    private Long version;
}
