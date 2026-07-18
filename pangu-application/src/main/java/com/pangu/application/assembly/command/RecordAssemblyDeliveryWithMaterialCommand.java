// 关联业务：用业主大会内已归档的送达凭证记录纸质选票送达。
package com.pangu.application.assembly.command;

public record RecordAssemblyDeliveryWithMaterialCommand(
        Long sessionId,
        Long tenantId,
        Long opid,
        String deliveryMethod,
        Long evidenceMaterialId,
        Long deliveredByUserId
) {
}
