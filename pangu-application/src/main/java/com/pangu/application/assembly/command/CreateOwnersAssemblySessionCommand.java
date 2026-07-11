package com.pangu.application.assembly.command;

public record CreateOwnersAssemblySessionCommand(
        Long tenantId,
        String title,
        String preparationMode,
        Long createdByUserId
) {
}
