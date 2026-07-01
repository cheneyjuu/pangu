package com.pangu.infrastructure.notification;

import com.pangu.domain.model.notification.VotingReminderDeliveryCommand;
import com.pangu.domain.model.notification.VotingReminderDeliveryGateway;
import com.pangu.infrastructure.persistence.mapper.VotingReminderDeliveryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "platform.voting.reminder-delivery-mode", havingValue = "database", matchIfMissing = true)
public class DatabaseVotingReminderDeliveryGateway implements VotingReminderDeliveryGateway {

    private final VotingReminderDeliveryMapper mapper;

    @Override
    public void deliver(VotingReminderDeliveryCommand command) {
        int inserted = mapper.enqueuePendingOwners(
                command.outboxEventId(),
                command.subjectId(),
                command.tenantId(),
                command.buildingId(),
                command.messageTemplate(),
                command.message());
        log.info("Voting reminder delivery expanded outboxEventId={} subjectId={} buildingId={} targetCount={} inserted={}",
                command.outboxEventId(), command.subjectId(), command.buildingId(), command.targetCount(), inserted);
    }
}
