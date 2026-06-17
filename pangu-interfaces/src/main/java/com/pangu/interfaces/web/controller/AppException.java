package com.pangu.interfaces.web.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 业务与安全统一异常类，对齐大厂主流设计方案并借鉴 lmpromotion 体系进行优化
 */
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object data;
    private final boolean needRetry;
    private final List<ErrorCode> errorChain;

    /**
     * 基于 ErrorCode 的构造函数
     */
    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = null;
        this.needRetry = errorCode.isNeedRetry();
        this.errorChain = new ArrayList<>();
        this.errorChain.add(errorCode);
    }

    /**
     * 基于 ErrorCode 且支持覆盖错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
        this.needRetry = errorCode.isNeedRetry();
        this.errorChain = new ArrayList<>();
        this.errorChain.add(errorCode);
    }

    /**
     * 基于 ErrorCode 且支持动态参数格式化错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, String messagePattern, Object... args) {
        super(safeFormat(messagePattern, args));
        this.errorCode = errorCode;
        this.data = null;
        this.needRetry = errorCode.isNeedRetry();
        this.errorChain = new ArrayList<>();
        this.errorChain.add(errorCode);
    }

    /**
     * 基于 ErrorCode 且支持带底层异常堆栈的构造函数
     * 借鉴 lmpromotion 架构，自适应汇聚错误追踪链 ErrorChain
     */
    public AppException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.data = null;
        this.errorChain = new ArrayList<>();
        if (cause instanceof AppException) {
            AppException causeAppEx = (AppException) cause;
            this.errorChain.addAll(causeAppEx.getErrorChain());
            this.needRetry = causeAppEx.isNeedRetry();
        } else {
            this.needRetry = errorCode.isNeedRetry();
        }
        this.errorChain.add(errorCode);
    }

    /**
     * 基于 ErrorCode、支持底层异常堆栈且支持动态参数格式化错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, Throwable cause, String messagePattern, Object... args) {
        super(safeFormat(messagePattern, args), cause);
        this.errorCode = errorCode;
        this.data = null;
        this.errorChain = new ArrayList<>();
        if (cause instanceof AppException) {
            AppException causeAppEx = (AppException) cause;
            this.errorChain.addAll(causeAppEx.getErrorChain());
            this.needRetry = causeAppEx.isNeedRetry();
        } else {
            this.needRetry = errorCode.isNeedRetry();
        }
        this.errorChain.add(errorCode);
    }

    /**
     * 基于 ErrorCode 且附带额外数据的构造函数
     */
    public AppException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
        this.needRetry = errorCode.isNeedRetry();
        this.errorChain = new ArrayList<>();
        this.errorChain.add(errorCode);
    }

    /**
     * 基于 ErrorCode、附带额外数据且支持动态参数格式化错误文案的构造函数
     */
    public AppException(ErrorCode errorCode, Object data, String messagePattern, Object... args) {
        super(safeFormat(messagePattern, args));
        this.errorCode = errorCode;
        this.data = data;
        this.needRetry = errorCode.isNeedRetry();
        this.errorChain = new ArrayList<>();
        this.errorChain.add(errorCode);
    }

    /**
     * 兼容老代码的构造函数
     */
    @Deprecated
    public AppException(int code, String message) {
        super(message);
        this.errorCode = new CustomErrorCode(code, message, code, "BIZ", false);
        this.data = null;
        this.needRetry = false;
        this.errorChain = new ArrayList<>();
        this.errorChain.add(this.errorCode);
    }

    /**
     * 兼容老代码的构造函数
     */
    @Deprecated
    public AppException(int code, String message, Object data) {
        super(message);
        this.errorCode = new CustomErrorCode(code, message, code, "BIZ", false);
        this.data = data;
        this.needRetry = false;
        this.errorChain = new ArrayList<>();
        this.errorChain.add(this.errorCode);
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

    private static class CustomErrorCode implements ErrorCode {
        private final int code;
        private final String message;
        private final int httpStatus;
        private final String errorType;
        private final boolean needRetry;

        public CustomErrorCode(int code, String message, int httpStatus, String errorType, boolean needRetry) {
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
            return errorType;
        }

        @Override
        public boolean isNeedRetry() {
            return needRetry;
        }
    }
}

