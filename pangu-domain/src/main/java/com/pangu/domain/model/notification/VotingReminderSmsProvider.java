package com.pangu.domain.model.notification;

public interface VotingReminderSmsProvider {

    VotingReminderDeliveryReceipt send(VotingReminderDeliveryItem item);
}
