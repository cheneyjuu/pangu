package com.pangu.interfaces.web.controller.dto.owner;

public record FaceAuthContextResponse(
        boolean eligible,
        String realName,
        String idCardNumber,
        String maskedRealName,
        String maskedIdCardNumber,
        String reason
) {
}
