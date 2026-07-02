package com.pangu.interfaces.web.exception;

import com.pangu.application.repair.RepairWorkOrderApplicationException;

public final class RepairWorkOrderExceptionTranslator {

    private RepairWorkOrderExceptionTranslator() {
    }

    public static RepairWorkOrderErrorCode translate(RepairWorkOrderApplicationException ex) {
        return switch (ex.reason()) {
            case PARAM_INVALID -> RepairWorkOrderErrorCode.PARAM_INVALID;
            case FORBIDDEN -> RepairWorkOrderErrorCode.FORBIDDEN;
            case NOT_FOUND -> RepairWorkOrderErrorCode.NOT_FOUND;
            case PROPERTY_NOT_OWNED -> RepairWorkOrderErrorCode.PROPERTY_NOT_OWNED;
            case BUILDING_NOT_IN_SCOPE -> RepairWorkOrderErrorCode.BUILDING_NOT_IN_SCOPE;
            case INVALID_STATUS -> RepairWorkOrderErrorCode.INVALID_STATUS;
            case LOCATION_NOT_VERIFIED -> RepairWorkOrderErrorCode.LOCATION_NOT_VERIFIED;
            case HANDOVER_LOCKED -> RepairWorkOrderErrorCode.HANDOVER_LOCKED;
        };
    }
}
