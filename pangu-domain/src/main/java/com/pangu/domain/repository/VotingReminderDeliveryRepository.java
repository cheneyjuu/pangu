package com.pangu.domain.repository;

import com.pangu.domain.model.notification.VotingReminderDeliveryItem;

import java.util.List;

public interface VotingReminderDeliveryRepository {

    List<VotingReminderDeliveryItem> claimPending(int limit);

    void markConfirmed(Long deliveryId, String providerMessageId);

    void markFailed(Long deliveryId, String errorMessage);
}
