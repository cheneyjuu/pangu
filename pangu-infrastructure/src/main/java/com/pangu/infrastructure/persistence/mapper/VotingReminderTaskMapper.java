package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.ReminderPendingOwnerRow;
import com.pangu.infrastructure.persistence.entity.ReminderTaskRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VotingReminderTaskMapper {

    List<ReminderTaskRow> listTasks(@Param("tenantId") Long tenantId,
                                    @Param("userId") Long userId);

    List<ReminderPendingOwnerRow> listPendingOwners(@Param("tenantId") Long tenantId,
                                                    @Param("userId") Long userId,
                                                    @Param("subjectId") Long subjectId);

    int markNotified(@Param("tenantId") Long tenantId,
                     @Param("userId") Long userId,
                     @Param("subjectId") Long subjectId,
                     @Param("uid") Long uid,
                     @Param("channel") String channel,
                     @Param("note") String note);
}
