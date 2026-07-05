package com.pangu.infrastructure.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.gateway.identity.IdCardOcrGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "platform.identity.id-card-ocr.provider-mode", havingValue = "tencent")
public class TencentIdCardOcrGateway implements IdCardOcrGateway {

    private final TencentCloudApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final String region;
    private final Duration timeout;

    public TencentIdCardOcrGateway(TencentCloudApiClient apiClient,
                                   ObjectMapper objectMapper,
                                   @Value("${platform.identity.tencent.region:ap-guangzhou}") String region,
                                   @Value("${platform.identity.tencent.timeout-millis:3000}") long timeoutMillis) {
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.region = region;
        this.timeout = Duration.ofMillis(timeoutMillis);
    }

    @Override
    public OcrResult recognize(OcrRequest request) {
        Map<String, Object> payload = payload(request);
        JsonNode response = apiClient.post("ocr", "IDCardOCR", "2018-11-19", region, payload, timeout);
        String name = text(response, "Name");
        String idNumber = text(response, "IdNum");
        String requestId = text(response, "RequestId");
        JsonNode advancedInfo = advancedInfo(response.path("AdvancedInfo"));
        Integer qualityScore = qualityScore(advancedInfo);
        List<String> warnings = warnings(advancedInfo);
        boolean recognized = StringUtils.hasText(name) && StringUtils.hasText(idNumber);
        return new OcrResult(
                recognized,
                "TENCENT_OCR",
                name,
                idNumber,
                requestId,
                qualityScore,
                warnings,
                recognized ? null : "身份证 OCR 未识别出姓名或证件号码");
    }

    private Map<String, Object> payload(OcrRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("id card ocr request must not be null");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        String imageBase64 = trimToNull(request.imageBase64());
        String imageUrl = trimToNull(request.imageUrl());
        if (imageBase64 == null && imageUrl == null) {
            throw new IllegalArgumentException("id card imageBase64 or imageUrl is required");
        }
        if (imageBase64 != null && imageUrl != null) {
            throw new IllegalArgumentException("id card imageBase64 and imageUrl cannot both be provided");
        }
        if (imageBase64 != null) {
            payload.put("ImageBase64", imageBase64);
        } else {
            payload.put("ImageUrl", imageUrl);
        }
        payload.put("CardSide", StringUtils.hasText(request.cardSide()) ? request.cardSide().trim() : CARD_SIDE_FRONT);
        payload.put("Config", """
                {"CropIdCard":false,"BorderCheckWarn":true,"CopyWarn":true,"ReshootWarn":true,"DetectPsWarn":true,"TempIdWarn":true,"InvalidDateWarn":true,"Quality":true,"MultiCardDetect":true,"ReflectWarn":true}
                """.trim());
        return payload;
    }

    private JsonNode advancedInfo(JsonNode raw) {
        try {
            if (raw == null || raw.isMissingNode() || raw.isNull() || !StringUtils.hasText(raw.asText())) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw.asText());
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private Integer qualityScore(JsonNode advancedInfo) {
        JsonNode quality = advancedInfo.path("Quality");
        if (quality.isInt()) {
            return quality.asInt();
        }
        if (quality.isNumber()) {
            return quality.numberValue().intValue();
        }
        if (quality.isObject() && quality.path("Score").isNumber()) {
            return quality.path("Score").asInt();
        }
        return null;
    }

    private List<String> warnings(JsonNode advancedInfo) {
        List<String> values = new ArrayList<>();
        JsonNode warnInfos = advancedInfo.path("WarnInfos");
        if (warnInfos.isArray()) {
            warnInfos.forEach(item -> {
                if (item.isTextual() && StringUtils.hasText(item.asText())) {
                    values.add(item.asText());
                } else if (item.isObject() && StringUtils.hasText(item.path("Code").asText())) {
                    values.add(item.path("Code").asText());
                }
            });
        }
        return values;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : trimToNull(value.asText());
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
