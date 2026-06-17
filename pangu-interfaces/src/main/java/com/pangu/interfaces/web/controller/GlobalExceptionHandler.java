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
        ErrorCode errCode = ex.getErrorCode();
        int httpStatus = errCode != null ? errCode.getHttpStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String errorType = errCode != null ? errCode.getErrorType() : "SYSTEM";
        response.setStatus(httpStatus);
        return Result.fail(ex.getCode(), ex.getMessage(), ex.getData(), errorType, ex.isNeedRetry());
    }
}
