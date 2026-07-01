package com.pangu.domain.repository;

import java.util.List;

public interface VotingReminderOutboxRepository {

    List<ReminderOutboxEvent> claimPending(int limit);

    void markConfirmed(Long eventId);

    void markFailed(Long eventId, String errorMessage);

    record ReminderOutboxEvent(
            Long eventId,
            Long businessRefId,
            Long tenantId,
            String payloadJson
    ) {
    }
}
