package com.pangu.application.dispute.command;

/** 业主撤回异议：RAISED / UNDER_REVIEW_* → WITHDRAWN。 */
public record WithdrawCommand(Long disputeId, Long requestByOwnerId) {
}
