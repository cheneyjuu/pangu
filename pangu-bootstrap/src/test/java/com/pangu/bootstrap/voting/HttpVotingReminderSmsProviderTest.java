package com.pangu.bootstrap.voting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.notification.VotingReminderDeliveryItem;
import com.pangu.infrastructure.notification.HttpVotingReminderSmsProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpVotingReminderSmsProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void send_postsDeliveryPayloadAndReadsProviderMessageId() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server(200, "{\"providerMessageId\":\"sms-1001\"}", authorization, requestBody);
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            HttpVotingReminderSmsProvider provider = new HttpVotingReminderSmsProvider(
                    objectMapper, endpoint, "token-1", 1000,
                    "", "", "", "X-Pangu-Signature", "X-Pangu-Timestamp", "", "");

            var receipt = provider.send(item());

            assertEquals("sms-1001", receipt.providerMessageId());
            assertEquals("Bearer token-1", authorization.get());
            JsonNode payload = objectMapper.readTree(requestBody.get());
            assertEquals(1L, payload.get("deliveryId").asLong());
            assertEquals(7001L, payload.get("subjectId").asLong());
            assertEquals("13800000012", payload.get("phone").asText());
            assertEquals("VOTE_REMINDER", payload.get("messageTemplate").asText());
            assertEquals("VOTE_REMINDER", payload.get("templateCode").asText());
            assertEquals(30001L, payload.get("templateParams").get("buildingId").asLong());
            assertEquals("请尽快投票", payload.get("message").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_readsConfiguredNestedProviderMessageIdField() throws Exception {
        HttpServer server = server(200, "{\"data\":{\"smsId\":\"nested-1001\"}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            HttpVotingReminderSmsProvider provider = new HttpVotingReminderSmsProvider(
                    objectMapper, endpoint, "", 1000,
                    "", "data.smsId", "", "X-Pangu-Signature", "X-Pangu-Timestamp", "", "");

            var receipt = provider.send(item());

            assertEquals("nested-1001", receipt.providerMessageId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_readsDefaultNestedProviderMessageIdField() throws Exception {
        HttpServer server = server(200, "{\"code\":0,\"data\":{\"smsId\":\"default-nested-1001\"}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            HttpVotingReminderSmsProvider provider = new HttpVotingReminderSmsProvider(
                    objectMapper, endpoint, "", 1000,
                    "", "", "", "X-Pangu-Signature", "X-Pangu-Timestamp", "code", "0");

            var receipt = provider.send(item());

            assertEquals("default-nested-1001", receipt.providerMessageId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_readsNumericProviderMessageIdField() throws Exception {
        HttpServer server = server(200, "{\"data\":{\"smsId\":100200300}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            HttpVotingReminderSmsProvider provider = new HttpVotingReminderSmsProvider(
                    objectMapper, endpoint, "", 1000,
                    "", "data.smsId", "", "X-Pangu-Signature", "X-Pangu-Timestamp", "", "");

            var receipt = provider.send(item());

            assertEquals("100200300", receipt.providerMessageId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_addsHmacSignatureHeadersAndConfiguredTemplateCode() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        HttpServer server = signedServer(200, "{\"requestId\":\"signed-1001\"}",
                requestBody, signature, timestamp);
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            HttpVotingReminderSmsProvider provider = new HttpVotingReminderSmsProvider(
                    objectMapper, endpoint, "", 1000,
                    "TPL_VOTE_REMINDER", "", "secret-1", "X-Sms-Signature", "X-Sms-Timestamp", "", "");

            var receipt = provider.send(item());

            JsonNode payload = objectMapper.readTree(requestBody.get());
            assertEquals("signed-1001", receipt.providerMessageId());
            assertEquals("TPL_VOTE_REMINDER", payload.get("templateCode").asText());
            assertEquals("请尽快投票", payload.get("templateParams").get("message").asText());
            assertEquals(hmacSha256Hex(timestamp.get() + "\n" + requestBody.get(), "secret-1"),
                    signature.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_validatesConfiguredBusinessSuccessCode() throws Exception {
        HttpServer server = server(200, "{\"code\":0,\"data\":{\"smsId\":\"ok-1001\"}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            HttpVotingReminderSmsProvider provider = new HttpVotingReminderSmsProvider(
                    objectMapper, endpoint, "", 1000,
                    "", "data.smsId", "", "X-Pangu-Signature", "X-Pangu-Timestamp", "code", "0");

            var receipt = provider.send(item());

            assertEquals("ok-1001", receipt.providerMessageId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_businessFailureCode_throwsEvenWhenProviderMessageIdExists() throws Exception {
        HttpServer server = server(200, "{\"code\":1001,\"data\":{\"smsId\":\"should-not-confirm\"}}",
                new AtomicReference<>(), new AtomicReference<>());
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            HttpVotingReminderSmsProvider provider = new HttpVotingReminderSmsProvider(
                    objectMapper, endpoint, "", 1000,
                    "", "data.smsId", "", "X-Pangu-Signature", "X-Pangu-Timestamp", "code", "0");

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> provider.send(item()));

            assertEquals(true, error.getMessage().contains("business failure"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void constructor_successCodeFieldWithoutValues_throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new HttpVotingReminderSmsProvider(
                        objectMapper, "http://127.0.0.1:19090/sms", "", 1000,
                        "", "data.smsId", "", "X-Pangu-Signature", "X-Pangu-Timestamp", "code", ""));

        assertEquals(true, error.getMessage().contains("success-code-values is required"));
    }

    @Test
    public void send_nonSuccessResponse_throws() throws Exception {
        HttpServer server = server(503, "{\"error\":\"busy\"}", new AtomicReference<>(), new AtomicReference<>());
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            HttpVotingReminderSmsProvider provider = new HttpVotingReminderSmsProvider(
                    objectMapper, endpoint, "", 1000,
                    "", "", "", "X-Pangu-Signature", "X-Pangu-Timestamp", "", "");

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> provider.send(item()));

            assertEquals(true, error.getMessage().contains("status=503"));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(int status,
                              String response,
                              AtomicReference<String> authorization,
                              AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sms", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer signedServer(int status,
                                    String response,
                                    AtomicReference<String> requestBody,
                                    AtomicReference<String> signature,
                                    AtomicReference<String> timestamp) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sms", exchange -> {
            signature.set(exchange.getRequestHeaders().getFirst("X-Sms-Signature"));
            timestamp.set(exchange.getRequestHeaders().getFirst("X-Sms-Timestamp"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String hmacSha256Hex(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private VotingReminderDeliveryItem item() {
        return new VotingReminderDeliveryItem(
                1L,
                2L,
                7001L,
                10001L,
                30001L,
                60001L,
                70001L,
                "13800000012",
                "SMS",
                "VOTE_REMINDER",
                "请尽快投票",
                1);
    }
}
