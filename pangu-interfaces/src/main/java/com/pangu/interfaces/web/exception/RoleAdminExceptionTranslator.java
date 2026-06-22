package com.pangu.interfaces.web.exception;

import com.pangu.application.admin.RoleAdminApplicationException;

/**
 * {@link RoleAdminApplicationException} → web 层 {@link RoleAdminErrorCode} 翻译器。
 */
public final class RoleAdminExceptionTranslator {

    private RoleAdminExceptionTranslator() {
    }

    public static RoleAdminErrorCode translate(RoleAdminApplicationException ex) {
        return switch (ex.getReason()) {
            case ROLE_NOT_FOUND -> RoleAdminErrorCode.ROLE_NOT_FOUND;
            case ROLE_KEY_DUPLICATE -> RoleAdminErrorCode.ROLE_KEY_DUPLICATE;
            case ROLE_PROTECTED -> RoleAdminErrorCode.ROLE_PROTECTED;
            case ROLE_SCOPE_LOCKED -> RoleAdminErrorCode.ROLE_SCOPE_LOCKED;
            case PERMISSION_ALREADY_ASSIGNED -> RoleAdminErrorCode.PERMISSION_ALREADY_ASSIGNED;
            case PERMISSION_NOT_ASSIGNED -> RoleAdminErrorCode.PERMISSION_NOT_ASSIGNED;
            case PERMISSION_ASSIGNMENT_INCONSISTENT -> RoleAdminErrorCode.PERMISSION_ASSIGNMENT_INCONSISTENT;
            case ROLE_PARAM_INVALID -> RoleAdminErrorCode.ROLE_PARAM_INVALID;
        };
    }
}
