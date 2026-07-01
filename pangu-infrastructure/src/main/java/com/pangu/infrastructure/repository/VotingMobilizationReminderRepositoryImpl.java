package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.VotingMobilizationReminder;
import com.pangu.domain.repository.VotingMobilizationReminderRepository;
import com.pangu.infrastructure.persistence.entity.VotingMobilizationReminderRow;
import com.pangu.infrastructure.persistence.mapper.VotingMobilizationReminderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VotingMobilizationReminderRepositoryImpl implements VotingMobilizationReminderRepository {

    private final VotingMobilizationReminderMapper mapper;

    @Override
    public int countUnvotedOwners(Long subjectId, Long tenantId, Long buildingId) {
        return mapper.countUnvotedOwners(subjectId, tenantId, buildingId);
    }

    @Override
    public VotingMobilizationReminder insert(VotingMobilizationReminder reminder) {
        VotingMobilizationReminderRow row = toRow(reminder);
        mapper.insert(row);
        reminder.setReminderId(row.getReminderId());
        return reminder;
    }

    private VotingMobilizationReminderRow toRow(VotingMobilizationReminder reminder) {
        VotingMobilizationReminderRow row = new VotingMobilizationReminderRow();
        row.setReminderId(reminder.getReminderId());
        row.setSubjectId(reminder.getSubjectId());
        row.setTenantId(reminder.getTenantId());
        row.setBuildingId(reminder.getBuildingId());
        row.setSentByUserId(reminder.getSentByUserId());
        row.setPermissionId(reminder.getPermissionId());
        row.setTargetScope(reminder.getTargetScope());
        row.setTargetCount(reminder.getTargetCount());
        row.setMessageTemplate(reminder.getMessageTemplate());
        row.setMessage(reminder.getMessage());
        row.setOutboxEventId(reminder.getOutboxEventId());
        row.setSentAt(reminder.getSentAt());
        return row;
    }
}
