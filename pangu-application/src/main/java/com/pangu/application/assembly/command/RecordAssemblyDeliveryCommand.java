package com.pangu.application.assembly.command;

public record RecordAssemblyDeliveryCommand(
        Long packageId,
        Long tenantId,
        Long opid,
        String deliveryChannel,
        String deliveryMethod,
        String evidenceHash,
        Long deliveredByUserId
) {
}
