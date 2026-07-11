package com.pangu.domain.model.repair;

import java.time.LocalDate;

/** 已审批且可用于免比价选商的长期合作关系。 */
public record RepairFrameworkRelation(
        Long relationId,
        Long supplierDeptId,
        String supplierLegalName,
        String serviceCategory,
        LocalDate validFrom,
        LocalDate validUntil
) {
}
