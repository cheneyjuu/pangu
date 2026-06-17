package com.pangu.interfaces.web.controller;

/**
 * 常用错误码枚举，对齐大厂主流异常规范
 */
public enum CommonErrorCode implements ErrorCode {

    // 成功 (200)
    SUCCESS(200, "操作成功", 200, ErrorType.BIZ, false),

    // 客户端请求错误 (400)
    PARAM_ERROR(400, "参数错误", 400, ErrorType.BIZ, false),
    BAD_REQUEST(400, "无效的请求", 400, ErrorType.BIZ, false),

    // 认证与安全错误 (401)
    UNAUTHORIZED(401, "未登录或认证失效", 401, ErrorType.BIZ, false),
    SMS_CODE_INVALID(401, "认证失败：短信验证码无效或已过期", 401, ErrorType.BIZ, false),
    SMS_CODE_EMPTY(401, "认证失败：短信验证码不能为空", 401, ErrorType.BIZ, false),
    USER_NOT_REGISTERED(401, "认证失败：该手机号未注册，请前往居委会完成线下实名核验登记", 401, ErrorType.BIZ, false),
    TOKEN_MISSING(401, "无访问权限：请携带 Token", 401, ErrorType.BIZ, false),

    // 授权错误 (403)
    FORBIDDEN(403, "没有访问权限", 403, ErrorType.BIZ, false),
    UNAUTHORIZED_TENANT(403, "越权访问：您在目标小区名下没有绑定的房产，拒绝切换", 403, ErrorType.BIZ, false),

    // 资源不存在 (404)
    NOT_FOUND(404, "资源不存在", 404, ErrorType.BIZ, false),

    // 方法不支持 (405)
    METHOD_NOT_ALLOWED(405, "请求方法不支持", 405, ErrorType.BIZ, false),

    // 服务器内部错误 (500)
    SYSTEM_ERROR(500, "系统内部繁忙，请稍后再试", 500, ErrorType.SYSTEM, true),
    SYSTEM_CONFIG_ERROR(500, "系统配置错误，请联系管理员", 500, ErrorType.SYSTEM, false);

    private final int code;
    private final String message;
    private final int httpStatus;
    private final ErrorType errorType;
    private final boolean needRetry;

    CommonErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.errorType = errorType;
        this.needRetry = needRetry;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getErrorType() {
        return errorType.name();
    }

    @Override
    public ErrorType getType() {
        return errorType;
    }

    @Override
    public boolean isNeedRetry() {
        return needRetry;
    }
}
