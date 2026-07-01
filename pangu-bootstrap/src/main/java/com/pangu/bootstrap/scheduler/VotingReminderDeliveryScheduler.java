package com.pangu.bootstrap.scheduler;

import com.pangu.application.voting.VotingReminderDeliveryDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VotingReminderDeliveryScheduler {

    private final VotingReminderDeliveryDispatchService dispatchService;

    @Value("${platform.voting.reminder-delivery-batch-size:50}")
    private int batchSize;

    @Scheduled(cron = "${platform.voting.reminder-delivery-cron:30 * * * * *}")
    public void tick() {
        try {
            int confirmed = dispatchService.dispatchPending(batchSize);
            if (confirmed > 0) {
                log.info("VotingReminderDeliveryScheduler tick: confirmed {} delivery records", confirmed);
            }
        } catch (RuntimeException ex) {
            log.error("VotingReminderDeliveryScheduler tick failed", ex);
        }
    }
}
