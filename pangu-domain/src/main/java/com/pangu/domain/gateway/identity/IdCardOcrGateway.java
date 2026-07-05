package com.pangu.domain.gateway.identity;

import java.util.List;

public interface IdCardOcrGateway {

    String CARD_SIDE_FRONT = "FRONT";

    OcrResult recognize(OcrRequest request);

    record OcrRequest(String imageBase64, String imageUrl, String cardSide) {
    }

    record OcrResult(
            boolean recognized,
            String provider,
            String realName,
            String idCardNumber,
            String requestId,
            Integer qualityScore,
            List<String> warnings,
            String reason
    ) {
        public OcrResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
