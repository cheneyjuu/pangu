package com.pangu.domain.repository;

import com.pangu.domain.model.voting.ReminderChannel;
import com.pangu.domain.model.voting.ReminderPendingOwner;
import com.pangu.domain.model.voting.ReminderTask;

import java.util.List;

public interface VotingReminderTaskRepository {

    List<ReminderTask> listTasks(Long tenantId, Long userId);

    List<ReminderPendingOwner> listPendingOwners(Long tenantId, Long userId, Long subjectId);

    int markNotified(Long tenantId,
                     Long userId,
                     Long subjectId,
                     Long uid,
                     ReminderChannel channel,
                     String note);
}
