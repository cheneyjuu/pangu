package com.pangu.bootstrap.voting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.application.voting.VotingReminderDeliveryDispatchService;
import com.pangu.domain.model.notification.VotingReminderDeliveryItem;
import com.pangu.domain.model.notification.VotingReminderDeliveryReceipt;
import com.pangu.domain.model.notification.VotingReminderSmsProvider;
import com.pangu.domain.repository.VotingReminderDeliveryRepository;
import com.pangu.infrastructure.notification.HttpVotingReminderSmsProvider;
import com.pangu.infrastructure.notification.MockVotingReminderSmsProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VotingReminderDeliveryDispatchServiceTest {

    @Mock
    private VotingReminderDeliveryRepository deliveryRepository;
    @Mock
    private VotingReminderSmsProvider smsProvider;

    private VotingReminderDeliveryDispatchService service() {
        return new VotingReminderDeliveryDispatchService(deliveryRepository, smsProvider);
    }

    @Test
    public void dispatchPending_success_marksConfirmed() {
        var item = item();
        when(deliveryRepository.claimPending(10)).thenReturn(List.of(item));
        when(smsProvider.send(item)).thenReturn(new VotingReminderDeliveryReceipt("provider-1"));

        int confirmed = service().dispatchPending(10);

        assertEquals(1, confirmed);
        verify(deliveryRepository).markConfirmed(1L, "provider-1");
        verify(deliveryRepository, never()).markFailed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void dispatchPending_providerFails_marksFailedAndContinues() {
        var item = item();
        when(deliveryRepository.claimPending(10)).thenReturn(List.of(item));
        doThrow(new IllegalStateException("provider unavailable")).when(smsProvider).send(item);

        int confirmed = service().dispatchPending(10);

        assertEquals(0, confirmed);
        verify(deliveryRepository).markFailed(1L, "provider unavailable");
        verify(deliveryRepository, never()).markConfirmed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void dispatchPending_withMockSmsProvider_marksConfirmed() {
        var item = item();
        when(deliveryRepository.claimPending(10)).thenReturn(List.of(item));

        int confirmed = new VotingReminderDeliveryDispatchService(
                deliveryRepository, new MockVotingReminderSmsProvider()).dispatchPending(10);

        assertEquals(1, confirmed);
        verify(deliveryRepository).markConfirmed(1L, "mock-sms-1");
        verify(deliveryRepository, never()).markFailed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void dispatchPending_withHttpProvider_postsSignedPayloadAndMarksConfirmed() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        HttpServer server = signedServer(requestBody, authorization, signature, timestamp,
                "{\"code\":0,\"data\":{\"smsId\":\"fake-sms-1\"}}");
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            VotingReminderSmsProvider httpProvider = new HttpVotingReminderSmsProvider(
                    new ObjectMapper(),
                    endpoint,
                    "local-token",
                    1000,
                    "TPL_VOTE_REMINDER",
                    "data.smsId",
                    "local-secret",
                    "X-Pangu-Signature",
                    "X-Pangu-Timestamp",
                    "code",
                    "0");
            var item = item();
            when(deliveryRepository.claimPending(10)).thenReturn(List.of(item));

            int confirmed = new VotingReminderDeliveryDispatchService(
                    deliveryRepository, httpProvider).dispatchPending(10);

            assertEquals(1, confirmed);
            verify(deliveryRepository).markConfirmed(1L, "fake-sms-1");
            verify(deliveryRepository, never()).markFailed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            assertEquals("Bearer local-token", authorization.get());
            assertEquals(hmacSha256Hex(timestamp.get() + "\n" + requestBody.get(), "local-secret"),
                    signature.get());
            JsonNode payload = new ObjectMapper().readTree(requestBody.get());
            assertEquals("TPL_VOTE_REMINDER", payload.get("templateCode").asText());
            assertEquals(7001L, payload.get("templateParams").get("subjectId").asLong());
            assertEquals("请尽快投票", payload.get("templateParams").get("message").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void dispatchPending_withHttpProviderBusinessFailure_marksFailed() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> signature = new AtomicReference<>();
        AtomicReference<String> timestamp = new AtomicReference<>();
        HttpServer server = signedServer(requestBody, authorization, signature, timestamp,
                "{\"code\":1001,\"data\":{\"smsId\":\"should-not-confirm\"}}");
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/sms";
            VotingReminderSmsProvider httpProvider = new HttpVotingReminderSmsProvider(
                    new ObjectMapper(),
                    endpoint,
                    "local-token",
                    1000,
                    "TPL_VOTE_REMINDER",
                    "data.smsId",
                    "local-secret",
                    "X-Pangu-Signature",
                    "X-Pangu-Timestamp",
                    "code",
                    "0");
            var item = item();
            when(deliveryRepository.claimPending(10)).thenReturn(List.of(item));

            int confirmed = new VotingReminderDeliveryDispatchService(
                    deliveryRepository, httpProvider).dispatchPending(10);

            assertEquals(0, confirmed);
            verify(deliveryRepository).markFailed(eq(1L), contains("business failure"));
            verify(deliveryRepository, never()).markConfirmed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        } finally {
            server.stop(0);
        }
    }

    private HttpServer signedServer(AtomicReference<String> requestBody,
                                    AtomicReference<String> authorization,
                                    AtomicReference<String> signature,
                                    AtomicReference<String> timestamp,
                                    String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sms", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            signature.set(exchange.getRequestHeaders().getFirst("X-Pangu-Signature"));
            timestamp.set(exchange.getRequestHeaders().getFirst("X-Pangu-Timestamp"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
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
                1L, 99001L, 7001L, 10001L, 30001L,
                50001L, 70001L, "13800000011", "SMS",
                "VOTE_REMINDER", "请尽快投票", 1);
    }
}
