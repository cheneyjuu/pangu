package com.pangu.domain.repository;

import com.pangu.domain.model.voting.VotingMobilizationReminder;

public interface VotingMobilizationReminderRepository {

    int countUnvotedOwners(Long subjectId, Long tenantId, Long buildingId);

    VotingMobilizationReminder insert(VotingMobilizationReminder reminder);
}
