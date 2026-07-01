package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VotingMobilizationReminder;

public interface VotingReminderOutboxGateway {

    Long enqueueReminderRequested(VotingMobilizationReminder reminder);
}
