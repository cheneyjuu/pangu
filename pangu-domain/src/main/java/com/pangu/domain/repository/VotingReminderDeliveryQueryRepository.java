package com.pangu.domain.repository;

import com.pangu.domain.model.notification.VotingReminderDeliveryStatus;

import java.util.List;

public interface VotingReminderDeliveryQueryRepository {

    List<VotingReminderDeliveryStatus> listBySubject(Long tenantId,
                                                     Long subjectId,
                                                     Long buildingId,
                                                     Integer deliveryStatus,
                                                     int limit);
}
