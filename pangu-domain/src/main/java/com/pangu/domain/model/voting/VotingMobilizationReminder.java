package com.pangu.domain.model.voting;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 投票期催票发送记录。
 */
@Data
@Builder
public class VotingMobilizationReminder {

    public static final String TARGET_SCOPE_UNVOTED_BUILDING_OWNERS = "UNVOTED_BUILDING_OWNERS";
    public static final String TEMPLATE_VOTE_REMINDER = "VOTE_REMINDER";

    private Long reminderId;
    private Long subjectId;
    private Long tenantId;
    private Long buildingId;
    private Long sentByUserId;
    private Long permissionId;
    private String targetScope;
    private Integer targetCount;
    private String messageTemplate;
    private String message;
    private Long outboxEventId;
    private Instant sentAt;
}
