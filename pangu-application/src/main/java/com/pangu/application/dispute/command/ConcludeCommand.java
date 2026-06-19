package com.pangu.application.dispute.command;

/** 业主接受最终决议：DECIDED_LEVEL_N_(UPHELD|PARTIAL) → CLOSED_FINAL。 */
public record ConcludeCommand(Long disputeId, Long requestByOwnerId) {
}
