package com.pangu.application.repair;

public record SupplierActivationResult(
        Long invitationId,
        Long supplierDeptId,
        String supplierLegalName,
        Long accountId,
        Long userId,
        String phone,
        String roleKey
) {
}
