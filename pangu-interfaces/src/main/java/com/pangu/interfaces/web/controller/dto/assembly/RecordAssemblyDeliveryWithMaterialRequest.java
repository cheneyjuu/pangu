// 关联业务：接收纸质选票送达记录，并将送达凭证从会内材料库中绑定到审计记录。
package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordAssemblyDeliveryWithMaterialRequest(
        @NotNull Long opid,
        @NotBlank @Size(max = 64) String deliveryMethod,
        @NotNull Long evidenceMaterialId
) {
}
