package com.pangu.domain.model.notification;

public interface VotingReminderDeliveryGateway {

    void deliver(VotingReminderDeliveryCommand command);
}
