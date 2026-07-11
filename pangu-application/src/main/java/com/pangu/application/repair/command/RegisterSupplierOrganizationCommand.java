package com.pangu.application.repair.command;

public record RegisterSupplierOrganizationCommand(
        String legalName,
        String unifiedSocialCreditCode,
        String contactName,
        String contactPhone
) {
}
