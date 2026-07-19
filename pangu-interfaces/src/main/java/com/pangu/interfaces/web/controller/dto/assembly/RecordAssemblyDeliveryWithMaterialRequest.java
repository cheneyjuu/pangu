// 关联业务：接收纸质选票送达记录，并将送达凭证从会内材料库中绑定到审计记录。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RecordAssemblyDeliveryWithMaterialRequest(
        @NotNull Long opid,
        @NotBlank String recipientName,
        @NotNull OwnersAssemblyRuleConfiguration.DeliveryMethod deliveryMethod,
        @NotNull Long evidenceMaterialId,
        @NotNull Instant deliveredAt
) {
}
