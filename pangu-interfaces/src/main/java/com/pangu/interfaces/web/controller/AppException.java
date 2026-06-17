package com.pangu.interfaces.web.controller;

/**
 * 业务与安全统一异常类，对齐大厂主流设计方案
 */
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object data;

    /**
     * 基于 ErrorCode 的构造函数
     */
    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = null;
    }

    /**
     * 基于 ErrorCode 且支持覆盖错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }

    /**
     * 基于 ErrorCode 且支持动态参数格式化错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, String messagePattern, Object... args) {
        super(safeFormat(messagePattern, args));
        this.errorCode = errorCode;
        this.data = null;
    }

    /**
     * 基于 ErrorCode 且支持带底层异常堆栈的构造函数
     */
    public AppException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.data = null;
    }

    /**
     * 基于 ErrorCode、支持底层异常堆栈且支持动态参数格式化错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, Throwable cause, String messagePattern, Object... args) {
        super(safeFormat(messagePattern, args), cause);
        this.errorCode = errorCode;
        this.data = null;
    }

    /**
     * 基于 ErrorCode 且附带额外数据的构造函数
     */
    public AppException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
    }

    /**
     * 基于 ErrorCode、附带额外数据且支持动态参数格式化错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, Object data, String messagePattern, Object... args) {
        super(safeFormat(messagePattern, args));
        this.errorCode = errorCode;
        this.data = data;
    }

    /**
     * 兼容老代码的构造函数
     */
    @Deprecated
    public AppException(int code, String message) {
        super(message);
        this.errorCode = new CustomErrorCode(code, message, code);
        this.data = null;
    }

    /**
     * 兼容老代码的构造函数
     */
    @Deprecated
    public AppException(int code, String message, Object data) {
        super(message);
        this.errorCode = new CustomErrorCode(code, message, code);
        this.data = data;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode != null ? errorCode.getCode() : 500;
    }

    public Object getData() {
        return data;
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

    private static class CustomErrorCode implements ErrorCode {
        private final int code;
        private final String message;
        private final int httpStatus;

        public CustomErrorCode(int code, String message, int httpStatus) {
            this.code = code;
            this.message = message;
            this.httpStatus = httpStatus;
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
    }
}

