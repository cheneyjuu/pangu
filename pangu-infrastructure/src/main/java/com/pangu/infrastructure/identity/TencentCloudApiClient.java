package com.pangu.infrastructure.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

@Component
public class TencentCloudApiClient {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final String secretId;
    private final String secretKey;

    @Autowired
    public TencentCloudApiClient(ObjectMapper objectMapper,
                                 @Value("${platform.identity.tencent.secret-id:}") String secretId,
                                 @Value("${platform.identity.tencent.secret-key:}") String secretKey) {
        this(objectMapper, secretId, secretKey, Clock.systemUTC());
    }

    TencentCloudApiClient(ObjectMapper objectMapper, String secretId, String secretKey, Clock clock) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
        this.clock = clock;
        this.secretId = blankToEmpty(secretId);
        this.secretKey = blankToEmpty(secretKey);
    }

    JsonNode post(String service,
                  String action,
                  String version,
                  String region,
                  Map<String, Object> payload,
                  Duration timeout) {
        requireCredentials();
        try {
            String host = service + ".tencentcloudapi.com";
            String body = objectMapper.writeValueAsString(payload);
            Instant now = clock.instant();
            long timestamp = now.getEpochSecond();
            String authorization = authorization(service, action, host, body, timestamp, now);

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://" + host))
                    .timeout(timeout)
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Host", host)
                    .header("X-TC-Action", action)
                    .header("X-TC-Version", version)
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (StringUtils.hasText(region)) {
                builder.header("X-TC-Region", region.trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("tencent cloud " + action + " returned status="
                        + response.statusCode() + " body=" + abbreviate(response.body()));
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            JsonNode responseNode = root.path("Response");
            JsonNode error = responseNode.path("Error");
            if (!error.isMissingNode()) {
                throw new IllegalStateException("tencent cloud " + action + " failed code="
                        + error.path("Code").asText("") + " message=" + error.path("Message").asText(""));
            }
            return responseNode;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("tencent cloud " + action + " interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("tencent cloud " + action + " request failed", e);
        }
    }

    private String authorization(String service,
                                 String action,
                                 String host,
                                 String body,
                                 long timestamp,
                                 Instant now) {
        String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + action.toLowerCase() + "\n";
        String signedHeaders = "content-type;host;x-tc-action";
        String canonicalRequest = "POST\n/\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + sha256Hex(body);

        String date = DATE_FORMATTER.format(now);
        String credentialScope = date + "/" + service + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));

        return "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private void requireCredentials() {
        if (!StringUtils.hasText(secretId) || !StringUtils.hasText(secretKey)) {
            throw new IllegalStateException("platform.identity.tencent.secret-id and secret-key are required");
        }
    }

    private byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("tencent cloud request signing failed", e);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("tencent cloud payload digest failed", e);
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500);
    }
}
