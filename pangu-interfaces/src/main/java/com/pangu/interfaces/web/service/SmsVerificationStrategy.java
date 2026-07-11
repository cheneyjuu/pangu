package com.pangu.interfaces.web.service;

import com.pangu.application.auth.SmsCodeVerifier;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;

/**
 * 短信验证码校验策略接口 (Strategy Pattern)
 */
public interface SmsVerificationStrategy extends SmsCodeVerifier {

    /**
     * 校验短信验证码是否正确
     * @param phone 手机号
     * @param smsCode 验证码
     */
    default void validate(String phone, String smsCode) {
        if (smsCode == null || smsCode.isBlank()) {
            throw new AppException(CommonErrorCode.SMS_CODE_EMPTY);
        }
        try {
            if (!verify(phone, smsCode)) {
                throw new AppException(CommonErrorCode.SMS_CODE_INVALID);
            }
        } catch (IllegalStateException ex) {
            throw new AppException(CommonErrorCode.SYSTEM_CONFIG_ERROR,
                    "系统配置错误：短信验证码服务不可用", ex);
        }
    }
}
