package com.pangu.interfaces.web.controller.dto.owner;

import java.util.List;

public record IdCardOcrResponse(
        boolean recognized,
        String provider,
        String realName,
        String idCardNumber,
        String maskedIdCardNumber,
        String requestId,
        Integer qualityScore,
        List<String> warnings,
        String reason
) {
    public IdCardOcrResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
