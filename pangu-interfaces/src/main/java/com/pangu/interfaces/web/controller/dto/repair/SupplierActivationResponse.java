package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.SupplierActivationResult;

public record SupplierActivationResponse(
        Long invitationId,
        Long supplierDeptId,
        String supplierLegalName,
        Long accountId,
        Long userId,
        String phone,
        String roleKey
) {
    public static SupplierActivationResponse from(SupplierActivationResult result) {
        return new SupplierActivationResponse(
                result.invitationId(), result.supplierDeptId(), result.supplierLegalName(),
                result.accountId(), result.userId(), result.phone(), result.roleKey());
    }
}
