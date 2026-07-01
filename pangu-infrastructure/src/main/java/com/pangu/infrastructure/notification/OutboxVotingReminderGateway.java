package com.pangu.infrastructure.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangu.domain.model.attestation.AttestationReceipt;
import com.pangu.domain.model.voting.VotingMobilizationReminder;
import com.pangu.domain.repository.VotingReminderOutboxGateway;
import com.pangu.infrastructure.persistence.entity.OutboxEventRow;
import com.pangu.infrastructure.persistence.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OutboxVotingReminderGateway implements VotingReminderOutboxGateway {

    private static final int EVENT_TYPE_VOTING_REMINDER_REQUESTED = 4;
    private static final String EVENT_NAME = "VOTING_REMINDER_REQUESTED";

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Long enqueueReminderRequested(VotingMobilizationReminder reminder) {
        OutboxEventRow row = new OutboxEventRow();
        row.setEventType(EVENT_TYPE_VOTING_REMINDER_REQUESTED);
        row.setBusinessRefId(reminder.getSubjectId());
        row.setTenantId(reminder.getTenantId());
        row.setPayloadJson(serialize(reminder));
        row.setStatus(AttestationReceipt.AttestationStatus.PENDING.getDbValue());
        outboxEventMapper.insert(row);
        return row.getEventId();
    }

    private String serialize(VotingMobilizationReminder reminder) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("eventType", EVENT_NAME);
        json.put("subjectId", reminder.getSubjectId());
        json.put("tenantId", reminder.getTenantId());
        json.put("buildingId", reminder.getBuildingId());
        json.put("sentByUserId", reminder.getSentByUserId());
        json.put("permissionId", reminder.getPermissionId());
        json.put("targetScope", reminder.getTargetScope());
        json.put("targetCount", reminder.getTargetCount());
        json.put("messageTemplate", reminder.getMessageTemplate());
        json.put("message", reminder.getMessage());
        json.put("sentAt", reminder.getSentAt().toString());
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("催票 outbox payload 序列化失败", ex);
        }
    }
}
