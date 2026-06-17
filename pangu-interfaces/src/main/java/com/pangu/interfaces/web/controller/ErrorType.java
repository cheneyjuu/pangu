package com.pangu.interfaces.web.controller;

/**
 * 错误大类，用于区分业务、系统和外部依赖错误。
 */
public enum ErrorType {

    /** 业务逻辑或客户端输入错误，通常不应重试。 */
    BIZ,

    /** 系统内部错误，通常可在短暂恢复后重试。 */
    SYSTEM,

    /** 第三方依赖错误，是否重试取决于具体错误码。 */
    THIRD_PARTY;

    public static ErrorType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return SYSTEM;
        }
        try {
            return ErrorType.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SYSTEM;
        }
    }
}
