package com.pangu.infrastructure.identity;

import com.pangu.domain.gateway.identity.IdCardOcrGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@ConditionalOnProperty(name = "platform.identity.id-card-ocr.provider-mode", havingValue = "mock", matchIfMissing = true)
public class MockIdCardOcrGateway implements IdCardOcrGateway {

    @Override
    public OcrResult recognize(OcrRequest request) {
        String raw = firstText(request.imageBase64(), request.imageUrl());
        if (StringUtils.hasText(raw) && raw.trim().startsWith("MOCK:")) {
            String[] parts = raw.trim().split(":", 3);
            if (parts.length == 3) {
                return new OcrResult(true, "MOCK", parts[1], parts[2],
                        "mock-idcard-ocr", 95, List.of(), null);
            }
        }
        return new OcrResult(true, "MOCK", "李四", "110101199003070011",
                "mock-idcard-ocr", 95, List.of(), null);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
