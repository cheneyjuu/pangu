package com.pangu.application.repair.command;

public record ActivateSupplierAccountCommand(
        Long invitationId,
        String phone,
        String smsCode,
        String operatorName
) {
}
