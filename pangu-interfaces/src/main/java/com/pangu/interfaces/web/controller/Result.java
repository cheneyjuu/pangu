package com.pangu.interfaces.web.controller;

/**
 * 统一 API 响应包装类
 */
public class Result<T> {
    private int code;
    private String msg;
    private T data;
    private String errorType;
    private Boolean needRetry;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public Result(int code, String msg, T data, String errorType, Boolean needRetry) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.errorType = errorType;
        this.needRetry = needRetry;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg, T data) {
        return new Result<>(code, msg, data);
    }

    public static <T> Result<T> fail(int code, String msg, T data, String errorType, Boolean needRetry) {
        return new Result<>(code, msg, data, errorType, needRetry);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public Boolean getNeedRetry() {
        return needRetry;
    }

    public void setNeedRetry(Boolean needRetry) {
        this.needRetry = needRetry;
    }
}
