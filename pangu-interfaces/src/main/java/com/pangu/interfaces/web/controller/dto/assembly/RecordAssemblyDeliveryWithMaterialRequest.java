// 关联业务：接收纸质选票送达记录，并将送达凭证从会内材料库中绑定到审计记录。
package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblyRuleConfiguration;
import jakarta.validation.constraints.NotNull;

public record RecordAssemblyDeliveryWithMaterialRequest(
        @NotNull Long opid,
        @NotNull OwnersAssemblyRuleConfiguration.DeliveryMethod deliveryMethod,
        @NotNull Long evidenceMaterialId
) {
}
