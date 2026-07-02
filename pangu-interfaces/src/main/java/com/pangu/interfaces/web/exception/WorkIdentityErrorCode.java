package com.pangu.interfaces.web.exception;

/**
 * 工作身份授权业务错误码。
 */
public enum WorkIdentityErrorCode implements ErrorCode {

    PARAM_INVALID(42501, "参数非法", 400, ErrorType.BIZ, false),
    FORBIDDEN(42502, "当前身份无权分配工作身份", 403, ErrorType.BIZ, false),
    ACCOUNT_NOT_FOUND(42503, "自然人账号不存在或不可用", 404, ErrorType.BIZ, false),
    ROLE_NOT_FOUND(42504, "角色不存在", 404, ErrorType.BIZ, false),
    DEPT_NOT_FOUND(42505, "部门不存在或已停用", 404, ErrorType.BIZ, false),
    DUPLICATE_IDENTITY(42506, "该自然人在该部门下已有工作身份", 409, ErrorType.BIZ, false),
    ROLE_DEPT_MISMATCH(42507, "角色与部门不匹配", 409, ErrorType.BIZ, false),
    BUILDING_REQUIRED(42508, "该角色必须绑定至少一个楼栋", 400, ErrorType.BIZ, false),
    BUILDING_NOT_ALLOWED(42509, "该角色不允许绑定楼栋", 400, ErrorType.BIZ, false),
    ROLE_BINDING_INCONSISTENT(42510, "角色绑定一致性失败", 409, ErrorType.BIZ, false);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    WorkIdentityErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
