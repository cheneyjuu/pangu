package com.pangu.interfaces.web.controller.dto.assembly;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordAssemblyDeliveryRequest(
        @NotNull Long opid,
        @NotBlank @Size(max = 32) String deliveryChannel,
        @NotBlank @Size(max = 64) String deliveryMethod,
        @NotBlank @Size(max = 128) String evidenceHash
) {
}
