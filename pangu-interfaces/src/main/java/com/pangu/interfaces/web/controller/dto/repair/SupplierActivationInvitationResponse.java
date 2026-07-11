package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.SupplierActivationInvitation;

import java.time.LocalDateTime;

public record SupplierActivationInvitationResponse(
        Long invitationId,
        Long supplierDeptId,
        String supplierLegalName,
        String contactName,
        String contactPhone,
        String status,
        LocalDateTime expiresAt
) {
    public static SupplierActivationInvitationResponse from(SupplierActivationInvitation invitation) {
        return new SupplierActivationInvitationResponse(
                invitation.invitationId(), invitation.supplierDeptId(), invitation.supplierLegalName(),
                invitation.contactName(), invitation.contactPhone(), invitation.status(), invitation.expiresAt());
    }
}
