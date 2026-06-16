package com.pangu.interfaces.web.controller;

/**
 * 统一控制器父类
 */
public abstract class BaseController {

    protected <T> Result<T> success(T data) {
        return Result.success(data);
    }

    protected <T> Result<T> success(String msg, T data) {
        return Result.success(msg, data);
    }

    protected <T> Result<T> fail(int code, String msg) {
        return Result.fail(code, msg);
    }

    protected <T> Result<T> fail(int code, String msg, T data) {
        return Result.fail(code, msg, data);
    }
}
