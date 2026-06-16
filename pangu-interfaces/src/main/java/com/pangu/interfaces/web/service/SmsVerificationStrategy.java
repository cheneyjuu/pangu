package com.pangu.interfaces.web.service;

/**
 * 短信验证码校验策略接口 (Strategy Pattern)
 */
public interface SmsVerificationStrategy {

    /**
     * 校验短信验证码是否正确
     * @param phone 手机号
     * @param smsCode 验证码
     */
    void validate(String phone, String smsCode);
}
