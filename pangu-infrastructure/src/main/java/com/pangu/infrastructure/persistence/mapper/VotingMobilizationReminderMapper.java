package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VotingMobilizationReminderRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VotingMobilizationReminderMapper {

    int countUnvotedOwners(@Param("subjectId") Long subjectId,
                           @Param("tenantId") Long tenantId,
                           @Param("buildingId") Long buildingId);

    int insert(VotingMobilizationReminderRow row);
}
