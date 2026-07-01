package com.pangu.infrastructure.notification;

import com.pangu.domain.model.notification.VotingReminderDeliveryCommand;
import com.pangu.domain.model.notification.VotingReminderDeliveryGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "platform.voting.reminder-delivery-mode", havingValue = "mock")
public class MockVotingReminderDeliveryGateway implements VotingReminderDeliveryGateway {

    @Override
    public void deliver(VotingReminderDeliveryCommand command) {
        log.info("Mock voting reminder delivered outboxEventId={} subjectId={} buildingId={} targetCount={}",
                command.outboxEventId(), command.subjectId(), command.buildingId(), command.targetCount());
    }
}
