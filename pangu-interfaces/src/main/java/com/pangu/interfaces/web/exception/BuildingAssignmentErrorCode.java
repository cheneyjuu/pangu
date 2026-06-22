package com.pangu.interfaces.web.exception;

/**
 * 楼栋责任田分配业务错误码（M4）。
 *
 * <p>段位规划：
 * <pre>
 *   42401  参数级（400）
 *   42402  角色禁用（403）—— 非主任/超管/书记不能分配
 *   42403  用户不存在/非可分配角色（404）
 *   42404  楼栋越界（403）—— 楼栋不在分配者租户
 *   42405  撤销记录不存在（404）
 * </pre>
 *
 * <p>与 {@link com.pangu.application.admin.BuildingAssignmentApplicationException.Reason}
 * 一一映射，由 {@link BuildingAssignmentExceptionTranslator} 转换。
 */
public enum BuildingAssignmentErrorCode implements ErrorCode {

    PARAM_INVALID(42401, "参数非法", 400, ErrorType.BIZ, false),
    FORBIDDEN(42402, "当前角色无权分配楼栋责任田", 403, ErrorType.BIZ, false),
    USER_NOT_FOUND(42403, "目标用户不存在或不持可分配角色", 404, ErrorType.BIZ, false),
    BUILDING_NOT_IN_SCOPE(42404, "楼栋不在分配者数据范围内", 403, ErrorType.BIZ, false),
    ASSIGNMENT_NOT_FOUND(42405, "无生效的楼栋分配记录，无需撤销", 404, ErrorType.BIZ, false);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    BuildingAssignmentErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
