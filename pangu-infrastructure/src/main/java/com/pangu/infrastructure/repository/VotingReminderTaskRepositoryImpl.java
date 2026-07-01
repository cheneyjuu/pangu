package com.pangu.infrastructure.repository;

import com.pangu.domain.model.voting.ReminderChannel;
import com.pangu.domain.model.voting.ReminderPendingOwner;
import com.pangu.domain.model.voting.ReminderTask;
import com.pangu.domain.model.voting.SubjectType;
import com.pangu.domain.repository.VotingReminderTaskRepository;
import com.pangu.infrastructure.persistence.entity.ReminderPendingOwnerRow;
import com.pangu.infrastructure.persistence.entity.ReminderTaskRow;
import com.pangu.infrastructure.persistence.mapper.VotingReminderTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class VotingReminderTaskRepositoryImpl implements VotingReminderTaskRepository {

    private final VotingReminderTaskMapper mapper;

    @Override
    public List<ReminderTask> listTasks(Long tenantId, Long userId) {
        return mapper.listTasks(tenantId, userId).stream().map(this::toTask).toList();
    }

    @Override
    public List<ReminderPendingOwner> listPendingOwners(Long tenantId, Long userId, Long subjectId) {
        return mapper.listPendingOwners(tenantId, userId, subjectId).stream()
                .map(this::toPendingOwner)
                .toList();
    }

    @Override
    public int markNotified(Long tenantId, Long userId, Long subjectId, Long uid,
                            ReminderChannel channel, String note) {
        return mapper.markNotified(tenantId, userId, subjectId, uid, channel.name(), note);
    }

    private ReminderTask toTask(ReminderTaskRow row) {
        return new ReminderTask(
                row.getSubjectId(),
                row.getSubjectTitle(),
                SubjectType.fromDbValue(row.getSubjectType()),
                row.getVoteEndAt(),
                row.getTotalCount() == null ? 0 : row.getTotalCount(),
                row.getPendingCount() == null ? 0 : row.getPendingCount());
    }

    private ReminderPendingOwner toPendingOwner(ReminderPendingOwnerRow row) {
        return new ReminderPendingOwner(
                row.getUid(),
                row.getNickName(),
                row.getPhoneMasked(),
                row.getBuildingId(),
                row.getRoomId(),
                channels(row.getNotifiedChannels()),
                row.getNotifiedAt(),
                row.getNote());
    }

    private List<ReminderChannel> channels(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .filter(s -> !s.isBlank())
                .map(ReminderChannel::valueOf)
                .toList();
    }
}
