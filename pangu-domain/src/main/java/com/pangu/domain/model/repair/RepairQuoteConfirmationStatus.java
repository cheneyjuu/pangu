package com.pangu.domain.model.repair;

/** 供应商报价证据和确认状态。 */
public enum RepairQuoteConfirmationStatus {
    PENDING_SUPPLIER_CONFIRMATION,
    ONLINE_CONFIRMED,
    OFFLINE_EVIDENCE_VERIFIED,
    CONTRACT_CONFIRMED;

    public boolean confirmedForContract() {
        return this != PENDING_SUPPLIER_CONFIRMATION;
    }
}
