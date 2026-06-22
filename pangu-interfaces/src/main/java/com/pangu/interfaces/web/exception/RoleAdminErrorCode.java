package com.pangu.interfaces.web.exception;

/**
 * SaaS 角色管理业务错误码（M2-4）。
 *
 * <p>段位规划：
 * <pre>
 *   421xx  参数级（VALIDATION / 400）
 *   422xx  业务冲突（BUSINESS / 404 / 409）
 *   423xx  权限保护（FORBIDDEN / 403）—— 系统预置角色禁止删除
 * </pre>
 *
 * <p>本枚举与 {@link com.pangu.application.admin.RoleAdminApplicationException.Reason}
 * 一一映射，由 {@link RoleAdminExceptionTranslator} 转换。
 */
public enum RoleAdminErrorCode implements ErrorCode {

    ROLE_PARAM_INVALID(42101, "角色参数非法", 400, ErrorType.BIZ, false),

    ROLE_NOT_FOUND(42201, "角色不存在", 404, ErrorType.BIZ, false),
    ROLE_KEY_DUPLICATE(42202, "role_key 已存在", 409, ErrorType.BIZ, false),
    PERMISSION_ALREADY_ASSIGNED(42203, "permission 已经授予该角色", 409, ErrorType.BIZ, false),
    PERMISSION_NOT_ASSIGNED(42205, "permission 未授予该角色，无需撤销", 404, ErrorType.BIZ, false),
    PERMISSION_ASSIGNMENT_INCONSISTENT(42204,
            "permission 授予一致性失败（端归属 / 红线 / 外键）", 409, ErrorType.BIZ, false),

    ROLE_PROTECTED(42301, "is_system=1 的预置角色禁止删除", 403, ErrorType.BIZ, false),

    ROLE_SCOPE_LOCKED(42302, "数据范围被法理红线锁死，不可在线变更", 403, ErrorType.BIZ, false);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    RoleAdminErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.needRetry = needRetry;
    }

    @Override public int getCode() { return code; }
    @Override public String getMessage() { return message; }
    @Override public int getHttpStatus() { return httpStatus; }
    @Override public String getErrorType() { return errorType.name(); }
    @Override public ErrorType getType() { return errorType; }
    @Override public boolean isNeedRetry() { return needRetry; }
}
