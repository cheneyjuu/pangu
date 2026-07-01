package com.pangu.infrastructure.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.notification.VotingReminderDeliveryItem;
import com.pangu.domain.model.notification.VotingReminderDeliveryReceipt;
import com.pangu.domain.model.notification.VotingReminderSmsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
@ConditionalOnProperty(name = "platform.voting.sms-provider-mode", havingValue = "http")
public class HttpVotingReminderSmsProvider implements VotingReminderSmsProvider {

    private static final List<String> DEFAULT_PROVIDER_MESSAGE_ID_FIELDS =
            List.of(
                    "providerMessageId",
                    "messageId",
                    "smsId",
                    "bizId",
                    "requestId",
                    "data.providerMessageId",
                    "data.messageId",
                    "data.smsId",
                    "data.bizId",
                    "data.requestId");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final URI endpoint;
    private final String bearerToken;
    private final Duration timeout;
    private final String templateCode;
    private final List<String> providerMessageIdFields;
    private final String signatureSecret;
    private final String signatureHeader;
    private final String signatureTimestampHeader;
    private final String successCodeField;
    private final List<String> successCodeValues;

    @Autowired
    public HttpVotingReminderSmsProvider(
            ObjectMapper objectMapper,
            @Value("${platform.voting.sms-provider.endpoint:}") String endpoint,
            @Value("${platform.voting.sms-provider.bearer-token:}") String bearerToken,
            @Value("${platform.voting.sms-provider.timeout-millis:3000}") long timeoutMillis,
            @Value("${platform.voting.sms-provider.template-code:}") String templateCode,
            @Value("${platform.voting.sms-provider.provider-message-id-fields:}") String providerMessageIdFields,
            @Value("${platform.voting.sms-provider.signature-secret:}") String signatureSecret,
            @Value("${platform.voting.sms-provider.signature-header:X-Pangu-Signature}") String signatureHeader,
            @Value("${platform.voting.sms-provider.signature-timestamp-header:X-Pangu-Timestamp}") String signatureTimestampHeader,
            @Value("${platform.voting.sms-provider.success-code-field:}") String successCodeField,
            @Value("${platform.voting.sms-provider.success-code-values:}") String successCodeValues) {
        this(objectMapper, endpoint, bearerToken, timeoutMillis, templateCode, providerMessageIdFields,
                signatureSecret, signatureHeader, signatureTimestampHeader, successCodeField, successCodeValues,
                Clock.systemUTC());
    }

    HttpVotingReminderSmsProvider(
            ObjectMapper objectMapper,
            String endpoint,
            String bearerToken,
            long timeoutMillis,
            String templateCode,
            String providerMessageIdFields,
            String signatureSecret,
            String signatureHeader,
            String signatureTimestampHeader,
            String successCodeField,
            String successCodeValues,
            Clock clock) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("platform.voting.sms-provider.endpoint is required when sms-provider-mode=http");
        }
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .build();
        this.clock = clock;
        this.endpoint = URI.create(endpoint);
        this.bearerToken = bearerToken == null ? "" : bearerToken.trim();
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.templateCode = templateCode == null ? "" : templateCode.trim();
        this.providerMessageIdFields = providerMessageIdFields(providerMessageIdFields);
        this.signatureSecret = signatureSecret == null ? "" : signatureSecret.trim();
        this.signatureHeader = StringUtils.hasText(signatureHeader) ? signatureHeader.trim() : "X-Pangu-Signature";
        this.signatureTimestampHeader = StringUtils.hasText(signatureTimestampHeader)
                ? signatureTimestampHeader.trim()
                : "X-Pangu-Timestamp";
        this.successCodeField = successCodeField == null ? "" : successCodeField.trim();
        this.successCodeValues = commaSeparatedValues(successCodeValues);
        if (StringUtils.hasText(this.successCodeField) && this.successCodeValues.isEmpty()) {
            throw new IllegalStateException(
                    "platform.voting.sms-provider.success-code-values is required when success-code-field is configured");
        }
    }

    @Override
    public VotingReminderDeliveryReceipt send(VotingReminderDeliveryItem item) {
        try {
            String body = objectMapper.writeValueAsString(payload(item));
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (StringUtils.hasText(bearerToken)) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            addSignatureHeaders(builder, body);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("sms provider returned status=" + response.statusCode()
                        + " body=" + abbreviate(response.body()));
            }
            JsonNode responseBody = responseBody(response.body());
            validateBusinessSuccess(responseBody);
            String providerMessageId = providerMessageId(responseBody);
            if (!StringUtils.hasText(providerMessageId)) {
                throw new IllegalStateException("sms provider response missing providerMessageId");
            }
            return new VotingReminderDeliveryReceipt(providerMessageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sms provider request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("sms provider request failed", e);
        }
    }

    private Map<String, Object> payload(VotingReminderDeliveryItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deliveryId", item.deliveryId());
        payload.put("outboxEventId", item.outboxEventId());
        payload.put("subjectId", item.subjectId());
        payload.put("tenantId", item.tenantId());
        payload.put("buildingId", item.buildingId());
        payload.put("opid", item.opid());
        payload.put("uid", item.uid());
        payload.put("phone", item.phone());
        payload.put("channel", item.channel());
        payload.put("messageTemplate", item.messageTemplate());
        payload.put("templateCode", StringUtils.hasText(templateCode) ? templateCode : item.messageTemplate());
        payload.put("templateParams", templateParams(item));
        payload.put("message", item.message());
        payload.put("attempts", item.attempts());
        return payload;
    }

    private Map<String, Object> templateParams(VotingReminderDeliveryItem item) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("subjectId", item.subjectId());
        params.put("tenantId", item.tenantId());
        params.put("buildingId", item.buildingId());
        params.put("opid", item.opid());
        params.put("uid", item.uid());
        params.put("message", item.message());
        return params;
    }

    private JsonNode responseBody(String body) throws JsonProcessingException {
        return objectMapper.readTree(body == null ? "{}" : body);
    }

    private void validateBusinessSuccess(JsonNode root) {
        if (!StringUtils.hasText(successCodeField)) {
            return;
        }
        JsonNode value = jsonPath(root, successCodeField);
        if (value == null || value.isMissingNode() || value.isNull()) {
            throw new IllegalStateException("sms provider response missing success code field=" + successCodeField);
        }
        String actual = value.asText();
        if (!successCodeValues.isEmpty() && !successCodeValues.contains(actual)) {
            throw new IllegalStateException("sms provider business failure field="
                    + successCodeField + " value=" + actual);
        }
    }

    private String providerMessageId(JsonNode root) {
        for (String field : providerMessageIdFields) {
            JsonNode value = jsonPath(root, field);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
            if (value != null && value.isNumber()) {
                return value.asText();
            }
        }
        return null;
    }

    private JsonNode jsonPath(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || !StringUtils.hasText(segment)) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private List<String> providerMessageIdFields(String raw) {
        List<String> fields = commaSeparatedValues(raw);
        return fields.isEmpty() ? DEFAULT_PROVIDER_MESSAGE_ID_FIELDS : fields;
    }

    private List<String> commaSeparatedValues(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void addSignatureHeaders(HttpRequest.Builder builder, String body) {
        if (!StringUtils.hasText(signatureSecret)) {
            return;
        }
        String timestamp = String.valueOf(clock.millis());
        builder.header(signatureTimestampHeader, timestamp);
        builder.header(signatureHeader, hmacSha256Hex(timestamp + "\n" + body, signatureSecret));
    }

    private String hmacSha256Hex(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sms provider signature failed", e);
        }
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500);
    }
}
