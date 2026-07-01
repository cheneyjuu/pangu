package com.pangu.bootstrap.scheduler;

import com.pangu.application.voting.VotingReminderOutboxConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VotingReminderOutboxScheduler {

    private final VotingReminderOutboxConsumerService consumerService;

    @Value("${platform.voting.reminder-outbox-batch-size:50}")
    private int batchSize;

    @Scheduled(cron = "${platform.voting.reminder-outbox-cron:15 * * * * *}")
    public void tick() {
        try {
            int delivered = consumerService.consumePending(batchSize);
            if (delivered > 0) {
                log.info("VotingReminderOutboxScheduler tick: delivered {} reminder events", delivered);
            }
        } catch (RuntimeException ex) {
            log.error("VotingReminderOutboxScheduler tick failed", ex);
        }
    }
}
