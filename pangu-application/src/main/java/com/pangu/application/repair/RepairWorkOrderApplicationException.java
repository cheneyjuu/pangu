package com.pangu.application.repair;

public class RepairWorkOrderApplicationException extends RuntimeException {

    public enum Reason {
        PARAM_INVALID,
        FORBIDDEN,
        NOT_FOUND,
        PROPERTY_NOT_OWNED,
        BUILDING_NOT_IN_SCOPE,
        INVALID_STATUS,
        LOCATION_NOT_VERIFIED,
        HANDOVER_LOCKED
    }

    private final Reason reason;

    public RepairWorkOrderApplicationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
