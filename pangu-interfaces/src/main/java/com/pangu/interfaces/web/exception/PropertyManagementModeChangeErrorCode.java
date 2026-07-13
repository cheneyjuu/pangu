// 关联业务：定义物业管理模式变更申请、审核和执行的稳定接口错误契约。
package com.pangu.interfaces.web.exception;

/**
 * 物业管理模式变更错误码。
 */
public enum PropertyManagementModeChangeErrorCode implements ErrorCode {

    PARAM_INVALID(42950, "物业管理模式变更参数非法", 400, ErrorType.BIZ, false),
    UNAUTHORIZED(42951, "物业管理模式变更登录状态无效", 401, ErrorType.BIZ, false),
    FORBIDDEN(42952, "当前身份无权办理物业管理模式变更", 403, ErrorType.BIZ, false),
    NOT_FOUND(42953, "物业管理模式变更申请或材料不存在", 404, ErrorType.BIZ, false),
    INVALID_STATUS(42954, "物业管理模式变更申请当前状态不可操作", 409, ErrorType.BIZ, false),
    MATERIAL_REQUIRED(42955, "提交前必须补齐业主大会决议材料", 400, ErrorType.BIZ, false),
    CONCURRENT_MODIFICATION(42956, "物业管理模式变更申请已被并发更新", 409, ErrorType.BIZ, true),
    DUPLICATE_ACTIVE_REQUEST(42957, "当前小区已有待处理的物业管理模式变更申请", 409, ErrorType.BIZ, false),
    STORAGE_UNAVAILABLE(42958, "物业管理模式变更材料存储暂不可用", 503, ErrorType.SYSTEM, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    PropertyManagementModeChangeErrorCode(
            int code,
            String message,
            int httpStatus,
            ErrorType errorType,
            boolean needRetry) {
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
