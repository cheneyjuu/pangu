package com.pangu.interfaces.web.controller;

/**
 * 错误码契约接口，参考 lmpromotion 体系对齐大厂主流异常规范
 */
public interface ErrorCode {
    
    /**
     * 获取业务错误码
     */
    int getCode();

    /**
     * 获取默认错误信息
     */
    String getMessage();

    /**
     * 获取对应的 HTTP 状态码
     */
    int getHttpStatus();

    /**
     * 获取错误大类 (如 BIZ-业务逻辑错误, SYSTEM-系统内部故障, THIRD_PARTY-外部第三方依赖错误)
     */
    String getErrorType();

    /**
     * 获取类型安全的错误大类。
     */
    default ErrorType getType() {
        return ErrorType.fromCode(getErrorType());
    }

    /**
     * 该错误是否支持/需要客户端或网关层进行重试 (如乐观锁冲突、临时并发故障等)
     */
    boolean isNeedRetry();
}
