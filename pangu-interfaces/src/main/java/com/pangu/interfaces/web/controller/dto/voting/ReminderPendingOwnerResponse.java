package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.ReminderChannel;
import com.pangu.domain.model.voting.ReminderPendingOwner;

import java.time.Instant;
import java.util.List;

public record ReminderPendingOwnerResponse(
        Long uid,
        String nickName,
        String phoneMasked,
        Long buildingId,
        Long roomId,
        List<ReminderChannel> notified,
        Instant notifiedAt,
        String note
) {
    public static ReminderPendingOwnerResponse from(ReminderPendingOwner owner) {
        return new ReminderPendingOwnerResponse(
                owner.uid(),
                owner.nickName(),
                owner.phoneMasked(),
                owner.buildingId(),
                owner.roomId(),
                owner.notified().isEmpty() ? null : owner.notified(),
                owner.notifiedAt(),
                owner.note());
    }
}
