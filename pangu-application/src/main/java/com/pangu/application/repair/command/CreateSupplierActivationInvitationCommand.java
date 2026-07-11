package com.pangu.application.repair.command;

public record CreateSupplierActivationInvitationCommand(
        String contactName,
        String contactPhone,
        Integer validHours
) {
}
