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
    SURVEY_COMPLETED,
    PLAN_SUBMITTED,
    QUOTE_COLLECTING,
    QUOTE_SUBMITTED,
    SUPPLIER_RECOMMENDED,
    LOCAL_DECISION_PENDING,
    LOCAL_DECISION_PASSED,
    ASSEMBLY_DECISION_PENDING,
    APPROVAL_DOCUMENT_PREPARING,
    PRICE_REVIEW_PENDING,
    GOVERNANCE_PENDING,
    GOVERNANCE_CONFIRMED,
    SEALED,
    CONTRACT_SIGNING,
    CONTRACT_EFFECTIVE,
    APPROVED,
    IN_PROGRESS,
    PENDING_ACCEPTANCE,
    ACCEPTANCE_EXCEPTION,
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
    HANDOVER_LOCK,
    EMERGENCY_REPORTED,
    EMERGENCY_MITIGATION,
    EMERGENCY_PLAN_PENDING,
    EMERGENCY_REPAIRING;

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
