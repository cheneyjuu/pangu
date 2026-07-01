package com.pangu.domain.model.voting;

import java.time.Instant;
import java.util.List;

public record ReminderPendingOwner(
        Long uid,
        String nickName,
        String phoneMasked,
        Long buildingId,
        Long roomId,
        List<ReminderChannel> notified,
        Instant notifiedAt,
        String note
) {
}
