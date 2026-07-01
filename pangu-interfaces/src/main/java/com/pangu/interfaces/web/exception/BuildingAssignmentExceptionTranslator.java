package com.pangu.interfaces.web.exception;

import com.pangu.application.admin.BuildingAssignmentApplicationException;

/**
 * {@link BuildingAssignmentApplicationException} → web 层 {@link BuildingAssignmentErrorCode} 翻译器。
 */
public final class BuildingAssignmentExceptionTranslator {

    private BuildingAssignmentExceptionTranslator() {
    }

    public static BuildingAssignmentErrorCode translate(BuildingAssignmentApplicationException ex) {
        return switch (ex.getReason()) {
            case PARAM_INVALID -> BuildingAssignmentErrorCode.PARAM_INVALID;
            case FORBIDDEN -> BuildingAssignmentErrorCode.FORBIDDEN;
            case USER_NOT_FOUND -> BuildingAssignmentErrorCode.USER_NOT_FOUND;
            case BUILDING_NOT_IN_SCOPE -> BuildingAssignmentErrorCode.BUILDING_NOT_IN_SCOPE;
            case ASSIGNMENT_NOT_FOUND -> BuildingAssignmentErrorCode.ASSIGNMENT_NOT_FOUND;
            case USER_NOT_COMPLIANT -> BuildingAssignmentErrorCode.USER_NOT_COMPLIANT;
            case BUILDING_OCCUPIED_BY_SAME_ROLE -> BuildingAssignmentErrorCode.BUILDING_OCCUPIED_BY_SAME_ROLE;
        };
    }
}
