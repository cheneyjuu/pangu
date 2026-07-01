package com.pangu.infrastructure.notification;

import com.pangu.domain.model.notification.VotingReminderDeliveryItem;
import com.pangu.domain.model.notification.VotingReminderDeliveryReceipt;
import com.pangu.domain.model.notification.VotingReminderSmsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "platform.voting.sms-provider-mode", havingValue = "mock", matchIfMissing = true)
public class MockVotingReminderSmsProvider implements VotingReminderSmsProvider {

    @Override
    public VotingReminderDeliveryReceipt send(VotingReminderDeliveryItem item) {
        log.info("Mock voting reminder sms sent deliveryId={} phone={} subjectId={} opid={}",
                item.deliveryId(), item.phone(), item.subjectId(), item.opid());
        return new VotingReminderDeliveryReceipt("mock-sms-" + item.deliveryId());
    }
}
