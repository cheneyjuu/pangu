package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairFrameworkRelation;

import java.time.LocalDate;

public record RepairFrameworkRelationResponse(
        Long relationId,
        Long supplierDeptId,
        String supplierLegalName,
        String serviceCategory,
        LocalDate validFrom,
        LocalDate validUntil
) {
    public static RepairFrameworkRelationResponse from(RepairFrameworkRelation relation) {
        return new RepairFrameworkRelationResponse(
                relation.relationId(),
                relation.supplierDeptId(),
                relation.supplierLegalName(),
                relation.serviceCategory(),
                relation.validFrom(),
                relation.validUntil());
    }
}
