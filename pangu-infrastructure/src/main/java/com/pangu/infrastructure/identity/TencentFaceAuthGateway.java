package com.pangu.infrastructure.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.gateway.identity.FaceAuthGateway;
import com.pangu.domain.model.identity.ChineseResidentId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "platform.identity.face-auth.provider-mode", havingValue = "tencent")
public class TencentFaceAuthGateway implements FaceAuthGateway {

    private final TencentCloudApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final String region;
    private final String ruleId;
    private final Duration timeout;
    private final double minSimilarity;

    public TencentFaceAuthGateway(TencentCloudApiClient apiClient,
                                  ObjectMapper objectMapper,
                                  @Value("${platform.identity.tencent.region:ap-guangzhou}") String region,
                                  @Value("${platform.identity.face-auth.tencent.rule-id:}") String ruleId,
                                  @Value("${platform.identity.tencent.timeout-millis:3000}") long timeoutMillis,
                                  @Value("${platform.identity.face-auth.tencent.min-similarity:70}") double minSimilarity) {
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.region = region;
        this.ruleId = ruleId == null ? "" : ruleId.trim();
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.minSimilarity = minSimilarity;
    }

    @Override
    public FaceAuthSession createSession(FaceAuthSessionRequest request) {
        requireRuleId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("RuleId", ruleId);
        payload.put("Name", request.realName());
        payload.put("IdCard", ChineseResidentId.normalize(request.idCardNumber()));
        if (StringUtils.hasText(request.extra())) {
            payload.put("Extra", request.extra().trim());
        }
        JsonNode response = apiClient.post("faceid", "DetectAuth", "2018-03-01", region, payload, timeout);
        String bizToken = text(response, "BizToken");
        String url = text(response, "Url");
        String requestId = text(response, "RequestId");
        if (!StringUtils.hasText(bizToken) || !StringUtils.hasText(url)) {
            throw new IllegalStateException("tencent face auth session missing BizToken or Url");
        }
        return new FaceAuthSession(PROVIDER_TENCENT_FACEID, bizToken, url, requestId, 7200);
    }

    @Override
    public FaceAuthVerificationResult verify(FaceAuthVerificationRequest request) {
        requireRuleId();
        String bizToken = request == null ? null : trimToNull(request.bizToken());
        if (!StringUtils.hasText(bizToken)) {
            return failed(null, "BizToken 不能为空");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("BizToken", bizToken);
        payload.put("RuleId", ruleId);
        payload.put("InfoType", "1");
        JsonNode response = apiClient.post("faceid", "GetDetectInfoEnhanced", "2018-03-01", region, payload, timeout);
        JsonNode text = textPayload(response.path("Text"));
        String requestId = text(response, "RequestId");
        String errCode = firstText(text, "ErrCode", "errCode");
        String liveStatus = firstText(text, "LiveStatus", "liveStatus");
        String compareStatus = firstText(text, "Comparestatus", "CompareStatus", "compareStatus");
        String actualName = firstText(text, "Name", "name");
        String actualIdCard = firstText(text, "IdCard", "IdCardNo", "idCard", "idcard");
        Double similarity = doubleValue(text, "Sim", "Similarity", "sim", "similarity");

        String summary = resultSummary(errCode, liveStatus, compareStatus, similarity, requestId);
        if (!isZero(errCode) || !isZero(liveStatus) || !isZero(compareStatus)) {
            return new FaceAuthVerificationResult(false, PROVIDER_TENCENT_FACEID, bizToken,
                    summary, requestId, "人脸核身未通过");
        }
        if (similarity != null && similarity < minSimilarity) {
            return new FaceAuthVerificationResult(false, PROVIDER_TENCENT_FACEID, bizToken,
                    summary, requestId, "人脸比对分低于阈值");
        }
        if (StringUtils.hasText(actualName) && !actualName.trim().equals(request.expectedRealName())) {
            return new FaceAuthVerificationResult(false, PROVIDER_TENCENT_FACEID, bizToken,
                    summary, requestId, "核身姓名与当前账号登记姓名不一致");
        }
        if (StringUtils.hasText(actualIdCard)
                && !ChineseResidentId.normalize(actualIdCard).equals(ChineseResidentId.normalize(request.expectedIdCardNumber()))) {
            return new FaceAuthVerificationResult(false, PROVIDER_TENCENT_FACEID, bizToken,
                    summary, requestId, "核身证件号与当前账号登记证件号不一致");
        }
        return new FaceAuthVerificationResult(true, PROVIDER_TENCENT_FACEID, bizToken, summary, requestId, null);
    }

    private FaceAuthVerificationResult failed(String providerRequestId, String reason) {
        return new FaceAuthVerificationResult(false, PROVIDER_TENCENT_FACEID, providerRequestId,
                "{\"verified\":false}", null, reason);
    }

    private void requireRuleId() {
        if (!StringUtils.hasText(ruleId)) {
            throw new IllegalStateException("platform.identity.face-auth.tencent.rule-id is required");
        }
    }

    private JsonNode textPayload(JsonNode raw) {
        try {
            if (raw == null || raw.isMissingNode() || raw.isNull()) {
                return objectMapper.createObjectNode();
            }
            if (raw.isObject()) {
                return raw;
            }
            if (StringUtils.hasText(raw.asText())) {
                return objectMapper.readTree(raw.asText());
            }
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String resultSummary(String errCode, String liveStatus, String compareStatus, Double similarity, String requestId) {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("errCode", errCode);
            summary.put("liveStatus", liveStatus);
            summary.put("compareStatus", compareStatus);
            summary.put("similarity", similarity);
            summary.put("requestId", requestId);
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "{\"summary\":\"unavailable\"}";
        }
    }

    private boolean isZero(String value) {
        return "0".equals(trimToNull(value));
    }

    private Double doubleValue(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                try {
                    return Double.parseDouble(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
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
