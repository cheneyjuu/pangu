package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.VotingReminderOutboxRepository;
import com.pangu.infrastructure.persistence.entity.OutboxEventRow;
import com.pangu.infrastructure.persistence.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class VotingReminderOutboxRepositoryImpl implements VotingReminderOutboxRepository {

    private final OutboxEventMapper mapper;

    @Override
    public List<ReminderOutboxEvent> claimPending(int limit) {
        return mapper.claimReminderPending(limit).stream()
                .map(row -> new ReminderOutboxEvent(
                        row.getEventId(),
                        row.getBusinessRefId(),
                        row.getTenantId(),
                        row.getPayloadJson()))
                .toList();
    }

    @Override
    public void markConfirmed(Long eventId) {
        mapper.markConfirmed(eventId);
    }

    @Override
    public void markFailed(Long eventId, String errorMessage) {
        mapper.markFailed(eventId, errorMessage);
    }
}
