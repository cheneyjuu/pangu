package com.pangu.application.dispute.command;

/** 业主走 Level 5 行政诉讼：RAISED 或 DECIDED_LEVEL_4_REJECTED → LITIGATION_FILED。 */
public record GotoLitigationCommand(Long disputeId, Long requestByOwnerId) {
}
