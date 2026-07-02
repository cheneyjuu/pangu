package com.pangu.domain.model.repair;

import java.util.Set;

/** 维修报修工单状态机。 */
public enum RepairWorkOrderStatus {
    SUBMITTED,
    PENDING_VERIFY,
    NEED_MANUAL_LOCATION,
    VERIFIED,
    ASSIGNED,
    SURVEYING,
    PLAN_SUBMITTED,
    GOVERNANCE_PENDING,
    APPROVED,
    IN_PROGRESS,
    PENDING_ACCEPTANCE,
    RECTIFICATION_REQUIRED,
    COMPLETED,
    EVALUATED,
    ARCHIVED,
    REJECTED,
    CANCELLED,
    SUSPENDED,
    ESCALATED,
    REASSIGN_REQUIRED,
    PLAN_REVISION_REQUIRED,
    CHANGE_REVIEW_PENDING,
    PAYMENT_EXCEPTION,
    HANDOVER_LOCK;

    private static final Set<RepairWorkOrderStatus> TERMINAL = Set.of(
            ARCHIVED, REJECTED, CANCELLED
    );

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean locationVerified() {
        return ordinal() >= VERIFIED.ordinal()
                && this != REJECTED
                && this != CANCELLED
                && this != SUSPENDED;
    }
}
