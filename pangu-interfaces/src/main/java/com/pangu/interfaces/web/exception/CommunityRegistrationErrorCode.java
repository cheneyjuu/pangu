// 关联业务：定义小区注册、材料审核和租户冷启动接口错误契约。
package com.pangu.interfaces.web.exception;

/**
 * 小区注册业务错误码。
 */
public enum CommunityRegistrationErrorCode implements ErrorCode {

    PARAM_INVALID(42901, "小区注册参数非法", 400, ErrorType.BIZ, false),
    UNAUTHORIZED(42902, "小区注册登录状态无效", 401, ErrorType.BIZ, false),
    FORBIDDEN(42903, "当前身份无权操作该小区注册申请", 403, ErrorType.BIZ, false),
    NOT_FOUND(42904, "小区注册申请或材料不存在", 404, ErrorType.BIZ, false),
    INVALID_STATUS(42905, "小区注册申请状态不可操作", 409, ErrorType.BIZ, false),
    DUPLICATE_APPLICATION(42906, "该小区已注册或存在未结束申请", 409, ErrorType.BIZ, false),
    MATERIAL_REQUIRED(42907, "提交审核前必须上传证明材料", 400, ErrorType.BIZ, false),
    CONCURRENT_MODIFICATION(42908, "申请已被并发更新", 409, ErrorType.BIZ, true),
    PROVISIONING_FAILED(42909, "小区租户开通失败", 409, ErrorType.BIZ, false),
    STORAGE_UNAVAILABLE(42910, "注册材料存储暂不可用", 503, ErrorType.SYSTEM, true);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    CommunityRegistrationErrorCode(
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
