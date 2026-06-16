package com.pangu.domain.model.user;

/**
 * 用户实名认证等级枚举 (L1-L4)
 */
public enum AuthenticationLevel {
    /** L1: 基础绑定 (手机号+短信) */
    L1(1),
    /** L3: 实名活体认证 (姓名+身份证号+人脸识别) */
    L3(3),
    /** L4: 司法级认证 (L3 + 企业营业执照/涉外证件人工联审) */
    L4(4);

    private final int value;

    AuthenticationLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static AuthenticationLevel of(int value) {
        for (AuthenticationLevel level : values()) {
            if (level.value == value) {
                return level;
            }
        }
        throw new IllegalArgumentException("未知的认证等级: " + value);
    }
}
