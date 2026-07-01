package com.pangu.infrastructure.persistence.mapper;

import com.pangu.infrastructure.persistence.entity.VotingReminderDeliveryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VotingReminderDeliveryMapper {

    int enqueuePendingOwners(@Param("outboxEventId") Long outboxEventId,
                             @Param("subjectId") Long subjectId,
                             @Param("tenantId") Long tenantId,
                             @Param("buildingId") Long buildingId,
                             @Param("messageTemplate") String messageTemplate,
                             @Param("message") String message);

    List<VotingReminderDeliveryRow> claimPending(@Param("limit") int limit);

    int markConfirmed(@Param("deliveryId") Long deliveryId,
                      @Param("providerMessageId") String providerMessageId);

    int markFailed(@Param("deliveryId") Long deliveryId,
                   @Param("lastError") String lastError);

    List<VotingReminderDeliveryRow> listBySubject(@Param("tenantId") Long tenantId,
                                                  @Param("subjectId") Long subjectId,
                                                  @Param("buildingId") Long buildingId,
                                                  @Param("deliveryStatus") Integer deliveryStatus,
                                                  @Param("limit") int limit);
}
