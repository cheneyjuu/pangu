package com.pangu.application.voting;

import com.pangu.domain.model.notification.VotingReminderDeliveryItem;
import com.pangu.domain.model.notification.VotingReminderSmsProvider;
import com.pangu.domain.repository.VotingReminderDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VotingReminderDeliveryDispatchService {

    private final VotingReminderDeliveryRepository deliveryRepository;
    private final VotingReminderSmsProvider smsProvider;

    public int dispatchPending(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<VotingReminderDeliveryItem> items = deliveryRepository.claimPending(safeLimit);
        int confirmed = 0;
        for (var item : items) {
            try {
                var receipt = smsProvider.send(item);
                deliveryRepository.markConfirmed(item.deliveryId(), receipt.providerMessageId());
                confirmed++;
            } catch (Exception ex) {
                deliveryRepository.markFailed(item.deliveryId(), truncate(ex.getMessage()));
                log.warn("Voting reminder delivery dispatch failed deliveryId={} subjectId={} uid={}",
                        item.deliveryId(), item.subjectId(), item.uid(), ex);
            }
        }
        return confirmed;
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown provider error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
