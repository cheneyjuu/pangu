package com.pangu.interfaces.web.service;

import com.pangu.interfaces.web.controller.AppException;
import com.pangu.interfaces.web.controller.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 针对开发和测试环境的 Mock 短信验证码校验策略
 */
@Component
@Profile({"dev", "test"})
public class MockSmsVerificationStrategy implements SmsVerificationStrategy {

    @Value("${platform.security.mock-sms-code:123456}")
    private String mockSmsCode;

    @Override
    public void validate(String phone, String smsCode) {
        if (smsCode == null || !mockSmsCode.equals(smsCode)) {
            throw new AppException(CommonErrorCode.SMS_CODE_INVALID);
        }
    }
}
