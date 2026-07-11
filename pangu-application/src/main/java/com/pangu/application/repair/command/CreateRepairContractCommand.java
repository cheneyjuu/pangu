package com.pangu.application.repair.command;

import java.math.BigDecimal;

public record CreateRepairContractCommand(
        Long supplierDeptId,
        String supplierName,
        BigDecimal contractAmount,
        String repairScopeHash,
        String fundSource,
        String signingMethod,
        String contractFileHash,
        String remark
) {
}
