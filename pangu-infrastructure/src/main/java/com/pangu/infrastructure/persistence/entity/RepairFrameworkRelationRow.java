package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RepairFrameworkRelationRow {
    private Long relationId;
    private Long supplierDeptId;
    private String supplierLegalName;
    private String serviceCategory;
    private LocalDate validFrom;
    private LocalDate validUntil;
}
