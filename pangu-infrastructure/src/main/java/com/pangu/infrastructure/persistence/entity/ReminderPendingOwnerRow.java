package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class ReminderPendingOwnerRow {
    private Long uid;
    private String nickName;
    private String phoneMasked;
    private Long buildingId;
    private Long roomId;
    private String notifiedChannels;
    private Instant notifiedAt;
    private String note;
}
