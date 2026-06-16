package com.pangu.interfaces.web.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一处理 AppException 异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Result<Object> handleAppException(AppException ex, HttpServletResponse response) {
        response.setStatus(ex.getCode());
        return Result.fail(ex.getCode(), ex.getMessage(), ex.getData());
    }
}
