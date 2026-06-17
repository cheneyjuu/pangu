package com.pangu.interfaces.web.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 业务与安全统一异常类，对齐大厂主流设计方案并借鉴 lmpromotion 体系进行优化
 */
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final boolean needRetry;
    private final List<ErrorCode> errorChain;

    /**
     * 基于 ErrorCode 的构造函数
     */
    public AppException(ErrorCode errorCode) {
        super(requireErrorCode(errorCode).getMessage());
        this.errorCode = errorCode;
        this.errorChain = new ArrayList<>();
        this.needRetry = initErrorChain(errorCode, null);
    }

    /**
     * 基于 ErrorCode 且支持覆盖错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorChain = new ArrayList<>();
        this.errorCode = requireErrorCode(errorCode);
        this.needRetry = initErrorChain(errorCode, null);
    }

    /**
     * 基于 ErrorCode 且支持动态参数格式化错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, String messagePattern, Object... args) {
        this(errorCode, safeFormat(messagePattern, args), true);
    }

    /**
     * 基于 ErrorCode 且支持带底层异常堆栈的构造函数
     * 借鉴 lmpromotion 架构，自适应汇聚错误追踪链 ErrorChain
     */
    public AppException(ErrorCode errorCode, Throwable cause) {
        super(requireErrorCode(errorCode).getMessage(), cause);
        this.errorCode = errorCode;
        this.errorChain = new ArrayList<>();
        this.needRetry = initErrorChain(errorCode, cause);
    }

    /**
     * 基于 ErrorCode、支持底层异常堆栈且支持动态参数格式化错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, Throwable cause, String messagePattern, Object... args) {
        this(errorCode, cause, safeFormat(messagePattern, args));
    }

    /**
     * 兼容老代码的构造函数
     */
    @Deprecated
    public AppException(int code, String message) {
        super(message);
        this.errorChain = new ArrayList<>();
        this.errorCode = new CustomErrorCode(code, message, normalizeHttpStatus(code),
                ErrorType.BIZ, false);
        this.needRetry = initErrorChain(this.errorCode, null);
    }

    private AppException(ErrorCode errorCode, String formattedMessage, boolean formatted) {
        super(formattedMessage);
        this.errorChain = new ArrayList<>();
        this.errorCode = requireErrorCode(errorCode);
        this.needRetry = initErrorChain(errorCode, null);
    }

    private AppException(ErrorCode errorCode, Throwable cause, String formattedMessage) {
        super(formattedMessage, cause);
        this.errorChain = new ArrayList<>();
        this.errorCode = requireErrorCode(errorCode);
        this.needRetry = initErrorChain(errorCode, cause);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode != null ? errorCode.getCode() : 500;
    }

    public boolean isNeedRetry() {
        return needRetry;
    }

    public List<ErrorCode> getErrorChain() {
        return Collections.unmodifiableList(errorChain);
    }

    private static String safeFormat(String pattern, Object... args) {
        if (pattern == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return String.format(pattern, args);
        } catch (Exception e) {
            // 格式化异常时降级返回原 pattern 拼接参数字符串
            StringBuilder sb = new StringBuilder(pattern);
            for (Object arg : args) {
                sb.append(" [").append(arg).append("]");
            }
            return sb.toString();
        }
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode must not be null");
        }
        return errorCode;
    }

    private boolean initErrorChain(ErrorCode currentErrorCode, Throwable cause) {
        ErrorCode checkedErrorCode = requireErrorCode(currentErrorCode);
        if (cause instanceof AppException causeAppEx) {
            this.errorChain.addAll(causeAppEx.getErrorChain());
            this.errorChain.add(checkedErrorCode);
            return causeAppEx.isNeedRetry();
        }
        this.errorChain.add(checkedErrorCode);
        return checkedErrorCode.isNeedRetry();
    }

    private static int normalizeHttpStatus(int code) {
        if (code >= 400 && code <= 599) {
            return code;
        }
        return CommonErrorCode.SYSTEM_ERROR.getHttpStatus();
    }

    private static class CustomErrorCode implements ErrorCode {
        private final int code;
        private final String message;
        private final int httpStatus;
        private final ErrorType errorType;
        private final boolean needRetry;

        public CustomErrorCode(int code, String message, int httpStatus, ErrorType errorType, boolean needRetry) {
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
}
