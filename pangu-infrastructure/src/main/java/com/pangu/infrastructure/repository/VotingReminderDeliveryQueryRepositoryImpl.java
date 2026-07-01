package com.pangu.infrastructure.repository;

import com.pangu.domain.model.notification.VotingReminderDeliveryStatus;
import com.pangu.domain.repository.VotingReminderDeliveryQueryRepository;
import com.pangu.infrastructure.persistence.entity.VotingReminderDeliveryRow;
import com.pangu.infrastructure.persistence.mapper.VotingReminderDeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class VotingReminderDeliveryQueryRepositoryImpl implements VotingReminderDeliveryQueryRepository {

    private final VotingReminderDeliveryMapper mapper;

    @Override
    public List<VotingReminderDeliveryStatus> listBySubject(Long tenantId,
                                                            Long subjectId,
                                                            Long buildingId,
                                                            Integer deliveryStatus,
                                                            int limit) {
        return mapper.listBySubject(tenantId, subjectId, buildingId, deliveryStatus, limit)
                .stream()
                .map(this::toStatus)
                .toList();
    }

    private VotingReminderDeliveryStatus toStatus(VotingReminderDeliveryRow row) {
        return new VotingReminderDeliveryStatus(
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
                row.getDeliveryStatus(),
                row.getAttempts(),
                row.getCreatedAt(),
                row.getLastAttemptAt(),
                row.getSubmittedAt(),
                row.getConfirmedAt(),
                row.getFailedAt(),
                row.getProviderMessageId(),
                row.getLastError());
    }
}
