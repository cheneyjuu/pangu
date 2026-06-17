package com.pangu.interfaces.web.exception;

/**
 * 业务与安全统一异常类。
 *
 * <p>设计原则：
 * <ul>
 *   <li>仅承载 {@link ErrorCode} 一个核心字段；其余如 needRetry / errorType / httpStatus
 *       全部从 ErrorCode 透传，避免重复缓存导致状态不一致。</li>
 *   <li>子类如需附加结构化响应数据，覆写 {@link #getResponsePayload()} 即可，
 *       由 {@code GlobalExceptionHandler} 统一挂载到 {@code Result.data}。</li>
 *   <li>不感知任何监控基础设施——HTTP 序列化、统计上报等由 ExceptionHandler 层组装。</li>
 * </ul>
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 使用错误码默认文案抛出异常。
     */
    public AppException(ErrorCode errorCode) {
        super(requireErrorCode(errorCode).getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用业务自定义文案抛出异常（覆盖 ErrorCode 的默认文案）。
     */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = requireErrorCode(errorCode);
    }

    /**
     * 包装底层异常时使用，文案沿用 ErrorCode 默认值。
     */
    public AppException(ErrorCode errorCode, Throwable cause) {
        super(requireErrorCode(errorCode).getMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 包装底层异常并自定义文案。
     */
    public AppException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = requireErrorCode(errorCode);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 客户端是否应针对该异常自动重试。透传自 {@link ErrorCode#isNeedRetry()}，不缓存。
     */
    public boolean isNeedRetry() {
        return errorCode.isNeedRetry();
    }

    /**
     * 子类可覆写以附加到响应体 {@code Result.data} 字段。
     *
     * <p>默认返回 {@code null}，{@link AppException} 本身不持有任何 payload 存储。
     * 这是有意为之：避免重新引入「所有异常都可挂任意数据」的开放槽位，
     * 仅在子类显式覆写时才暴露结构化业务上下文（例如 {@code CandidacyRestrictedException}）。
     */
    public Object getResponsePayload() {
        return null;
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode must not be null");
        }
        return errorCode;
    }
}
