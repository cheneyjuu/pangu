package com.pangu.application.voting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.notification.VotingReminderDeliveryCommand;
import com.pangu.domain.model.notification.VotingReminderDeliveryGateway;
import com.pangu.domain.repository.VotingReminderOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VotingReminderOutboxConsumerService {

    private final VotingReminderOutboxRepository outboxRepository;
    private final VotingReminderDeliveryGateway deliveryGateway;
    private final ObjectMapper objectMapper;

    public int consumePending(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<VotingReminderOutboxRepository.ReminderOutboxEvent> events =
                outboxRepository.claimPending(safeLimit);
        int delivered = 0;
        for (VotingReminderOutboxRepository.ReminderOutboxEvent event : events) {
            try {
                deliveryGateway.deliver(toCommand(event));
                outboxRepository.markConfirmed(event.eventId());
                delivered++;
            } catch (Exception ex) {
                outboxRepository.markFailed(event.eventId(), truncate(ex.getMessage()));
                log.warn("Voting reminder outbox delivery failed eventId={} tenantId={} businessRefId={}",
                        event.eventId(), event.tenantId(), event.businessRefId(), ex);
            }
        }
        return delivered;
    }

    private VotingReminderDeliveryCommand toCommand(VotingReminderOutboxRepository.ReminderOutboxEvent event) {
        try {
            JsonNode root = objectMapper.readTree(event.payloadJson());
            return new VotingReminderDeliveryCommand(
                    event.eventId(),
                    longValue(root, "subjectId", event.businessRefId()),
                    longValue(root, "tenantId", event.tenantId()),
                    requiredLong(root, "buildingId"),
                    requiredLong(root, "sentByUserId"),
                    nullableLong(root, "permissionId"),
                    textValue(root, "targetScope"),
                    intValue(root, "targetCount", 0),
                    textValue(root, "messageTemplate"),
                    nullableText(root, "message"),
                    instantValue(root, "sentAt"));
        } catch (Exception ex) {
            throw new IllegalStateException("催票 outbox payload 解析失败 eventId=" + event.eventId(), ex);
        }
    }

    private Long requiredLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.canConvertToLong()) {
            throw new IllegalArgumentException("payload 缺少字段 " + field);
        }
        return node.asLong();
    }

    private Long nullableLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asLong();
    }

    private Long longValue(JsonNode root, String field, Long defaultValue) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? defaultValue : node.asLong();
    }

    private Integer intValue(JsonNode root, String field, Integer defaultValue) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? defaultValue : node.asInt();
    }

    private String textValue(JsonNode root, String field) {
        String value = nullableText(root, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("payload 缺少字段 " + field);
        }
        return value;
    }

    private String nullableText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private Instant instantValue(JsonNode root, String field) {
        String value = textValue(root, field);
        return Instant.parse(value);
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown delivery error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
