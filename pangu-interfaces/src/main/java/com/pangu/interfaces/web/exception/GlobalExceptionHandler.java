package com.pangu.interfaces.web.exception;

import com.pangu.interfaces.web.controller.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一处理 {@link AppException} 及未捕获异常。
 *
 * <p>职责边界：
 * <ul>
 *   <li>将 {@link ErrorCode} 中的 httpStatus / errorType / needRetry 翻译到 HTTP 响应；</li>
 *   <li>调用 {@link AppException#getResponsePayload()} 获取子类附加的结构化数据，无需 {@code instanceof} 分支；</li>
 *   <li>未知异常一律降级为 {@link CommonErrorCode#SYSTEM_ERROR}，避免堆栈泄漏到客户端。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Result<Object> handleAppException(AppException ex, HttpServletResponse response) {
        ErrorCode errorCode = ex.getErrorCode();
        response.setStatus(errorCode.getHttpStatus());
        return Result.fail(
                errorCode.getCode(),
                ex.getMessage(),
                ex.getResponsePayload(),
                errorCode.getErrorType(),
                ex.isNeedRetry());
    }

    @ExceptionHandler(Exception.class)
    public Result<Object> handleUnexpectedException(Exception ex, HttpServletResponse response) {
        CommonErrorCode errorCode = CommonErrorCode.SYSTEM_ERROR;
        response.setStatus(errorCode.getHttpStatus());
        return Result.fail(
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                errorCode.getErrorType(),
                errorCode.isNeedRetry());
    }
}
