package com.pangu.interfaces.web.controller;

/**
 * 业务与安全统一异常类
 */
public class AppException extends RuntimeException {
    private final int code;
    private final Object data;

    public AppException(int code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public AppException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
