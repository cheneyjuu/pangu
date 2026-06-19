package com.pangu.application.dispute.command;

/** 业主升级到下一级：DECIDED_LEVEL_N_REJECTED → UNDER_REVIEW_LEVEL_N+1。 */
public record EscalateCommand(Long disputeId, Long requestByOwnerId) {
}
