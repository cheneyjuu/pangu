package com.pangu.infrastructure.repository;

import com.pangu.domain.model.notification.VotingReminderDeliveryItem;
import com.pangu.domain.repository.VotingReminderDeliveryRepository;
import com.pangu.infrastructure.persistence.entity.VotingReminderDeliveryRow;
import com.pangu.infrastructure.persistence.mapper.VotingReminderDeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class VotingReminderDeliveryRepositoryImpl implements VotingReminderDeliveryRepository {

    private final VotingReminderDeliveryMapper mapper;

    @Override
    public List<VotingReminderDeliveryItem> claimPending(int limit) {
        return mapper.claimPending(limit).stream()
                .map(this::toItem)
                .toList();
    }

    @Override
    public void markConfirmed(Long deliveryId, String providerMessageId) {
        mapper.markConfirmed(deliveryId, providerMessageId);
    }

    @Override
    public void markFailed(Long deliveryId, String errorMessage) {
        mapper.markFailed(deliveryId, errorMessage);
    }

    private VotingReminderDeliveryItem toItem(VotingReminderDeliveryRow row) {
        return new VotingReminderDeliveryItem(
                row.getDeliveryId(),
                row.getOutboxEventId(),
                row.getSubjectId(),
                row.getTenantId(),
                row.getBuildingId(),
                row.getOpid(),
                row.getUid(),
                row.getPhone(),
                row.getChannel(),
                row.getMessageTemplate(),
                row.getMessage(),
                row.getAttempts());
    }
}
