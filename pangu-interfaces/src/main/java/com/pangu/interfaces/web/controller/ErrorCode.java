package com.pangu.interfaces.web.controller;

/**
 * 错误码契约接口，对齐大厂主流异常规范
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
}
