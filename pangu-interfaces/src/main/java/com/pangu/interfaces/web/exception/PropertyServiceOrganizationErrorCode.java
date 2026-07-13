// 关联业务：定义物业服务组织登记、企业核验和项目部启用的稳定接口错误契约。
package com.pangu.interfaces.web.exception;

/**
 * 物业服务组织登记错误码。
 */
public enum PropertyServiceOrganizationErrorCode implements ErrorCode {

    PARAM_INVALID(42940, "物业服务组织参数非法", 400, ErrorType.BIZ, false),
    UNAUTHORIZED(42941, "物业服务组织登录状态无效", 401, ErrorType.BIZ, false),
    FORBIDDEN(42942, "当前身份无权办理物业服务组织", 403, ErrorType.BIZ, false),
    NOT_FOUND(42943, "物业服务组织或材料不存在", 404, ErrorType.BIZ, false),
    INVALID_STATUS(42944, "物业服务组织当前状态不可操作", 409, ErrorType.BIZ, false),
    MATERIAL_REQUIRED(42945, "提交核验前必须补齐物业服务组织材料", 400, ErrorType.BIZ, false),
    CONCURRENT_MODIFICATION(42946, "物业服务组织已被并发更新", 409, ErrorType.BIZ, true),
    DUPLICATE_ACTIVE_ORGANIZATION(42947, "当前小区已启用物业服务组织", 409, ErrorType.BIZ, false),
    STORAGE_UNAVAILABLE(42948, "物业服务组织材料存储暂不可用", 503, ErrorType.SYSTEM, true),
    EXTERNAL_VERIFICATION_UNAVAILABLE(42949, "企业核验平台暂不可用", 503, ErrorType.THIRD_PARTY, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    PropertyServiceOrganizationErrorCode(
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
