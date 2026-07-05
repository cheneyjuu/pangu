package com.pangu.infrastructure.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.gateway.identity.FaceAuthGateway;
import com.pangu.domain.gateway.identity.IdCardOcrGateway;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TencentIdentityGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void idCardOcr_parsesIdentityAndRiskWarnings() {
        FakeTencentCloudApiClient client = new FakeTencentCloudApiClient(objectMapper)
                .withResponse("IDCardOCR", Map.of(
                        "Name", "李四",
                        "IdNum", "110101199003070011",
                        "RequestId", "ocr-request-1",
                        "AdvancedInfo", "{\"Quality\":88,\"WarnInfos\":[\"copy\",\"reflect\"]}"
                ));
        TencentIdCardOcrGateway gateway = new TencentIdCardOcrGateway(client, objectMapper, "ap-guangzhou", 3000);

        IdCardOcrGateway.OcrResult result = gateway.recognize(
                new IdCardOcrGateway.OcrRequest("base64-image", null, "FRONT"));

        assertTrue(result.recognized());
        assertEquals("TENCENT_OCR", result.provider());
        assertEquals("李四", result.realName());
        assertEquals("110101199003070011", result.idCardNumber());
        assertEquals(88, result.qualityScore());
        assertEquals(2, result.warnings().size());
    }

    @Test
    void faceAuth_createsSessionAndVerifiesServerSideResult() {
        FakeTencentCloudApiClient client = new FakeTencentCloudApiClient(objectMapper)
                .withResponse("DetectAuth", Map.of(
                        "BizToken", "biz-token-1",
                        "Url", "weixin://faceid/session",
                        "RequestId", "detect-request-1"
                ))
                .withResponse("GetDetectInfoEnhanced", Map.of(
                        "RequestId", "verify-request-1",
                        "Text", "{\"ErrCode\":\"0\",\"LiveStatus\":\"0\",\"Comparestatus\":\"0\",\"Name\":\"李四\",\"IdCard\":\"110101199003070011\",\"Sim\":\"86.5\"}"
                ));
        TencentFaceAuthGateway gateway = new TencentFaceAuthGateway(
                client, objectMapper, "ap-guangzhou", "rule-1", 3000, 70);

        FaceAuthGateway.FaceAuthSession session = gateway.createSession(
                new FaceAuthGateway.FaceAuthSessionRequest("李四", "110101199003070011", "extra"));
        FaceAuthGateway.FaceAuthVerificationResult result = gateway.verify(
                new FaceAuthGateway.FaceAuthVerificationRequest(
                        "TENCENT_FACEID", session.bizToken(), "李四", "110101199003070011"));

        assertEquals("biz-token-1", session.bizToken());
        assertEquals("weixin://faceid/session", session.url());
        assertTrue(result.verified());
        assertEquals("TENCENT_FACEID", result.provider());
        assertEquals("biz-token-1", result.providerRequestId());
    }

    @Test
    void faceAuth_rejectsIdentityMismatchFromProviderResult() {
        FakeTencentCloudApiClient client = new FakeTencentCloudApiClient(objectMapper)
                .withResponse("GetDetectInfoEnhanced", Map.of(
                        "RequestId", "verify-request-2",
                        "Text", "{\"ErrCode\":\"0\",\"LiveStatus\":\"0\",\"Comparestatus\":\"0\",\"Name\":\"王五\",\"IdCard\":\"110101199003070011\",\"Sim\":\"90\"}"
                ));
        TencentFaceAuthGateway gateway = new TencentFaceAuthGateway(
                client, objectMapper, "ap-guangzhou", "rule-1", 3000, 70);

        FaceAuthGateway.FaceAuthVerificationResult result = gateway.verify(
                new FaceAuthGateway.FaceAuthVerificationRequest(
                        "TENCENT_FACEID", "biz-token-2", "李四", "110101199003070011"));

        assertFalse(result.verified());
        assertEquals("核身姓名与当前账号登记姓名不一致", result.failureReason());
    }

    private static class FakeTencentCloudApiClient extends TencentCloudApiClient {
        private final ObjectMapper objectMapper;
        private final Map<String, Map<String, Object>> responses = new LinkedHashMap<>();

        FakeTencentCloudApiClient(ObjectMapper objectMapper) {
            super(objectMapper, "secret-id", "secret-key", Clock.systemUTC());
            this.objectMapper = objectMapper;
        }

        FakeTencentCloudApiClient withResponse(String action, Map<String, Object> response) {
            responses.put(action, response);
            return this;
        }

        @Override
        JsonNode post(String service,
                      String action,
                      String version,
                      String region,
                      Map<String, Object> payload,
                      Duration timeout) {
            return objectMapper.valueToTree(responses.getOrDefault(action, Map.of()));
        }
    }
}
