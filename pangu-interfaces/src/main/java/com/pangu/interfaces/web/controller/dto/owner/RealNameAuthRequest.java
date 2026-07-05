package com.pangu.interfaces.web.controller.dto.owner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RealNameAuthRequest(
        @NotBlank(message = "realName must not be blank")
        @Size(min = 2, max = 32, message = "realName length must be between 2 and 32")
        String realName,

        @NotBlank(message = "idCardNumber must not be blank")
        @Size(min = 18, max = 18, message = "idCardNumber length must be 18")
        String idCardNumber
) {
}
