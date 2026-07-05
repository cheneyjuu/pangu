package com.pangu.interfaces.web.service;

import com.pangu.domain.repository.SmsVerificationCodeRepository;
import com.pangu.interfaces.web.exception.AppException;
import com.pangu.interfaces.web.exception.CommonErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * 针对生产环境的真实短信验证码校验策略 (基于 Redis 缓存服务)
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdSmsVerificationStrategy implements SmsVerificationStrategy {

    private final SmsVerificationCodeRepository smsVerificationCodeRepository;

    @Override
    public void validate(String phone, String smsCode) {
        if (smsCode == null || smsCode.isEmpty()) {
            throw new AppException(CommonErrorCode.SMS_CODE_EMPTY);
        }

        try {
            if (!smsVerificationCodeRepository.consumeIfMatches(phone, smsCode)) {
                throw new AppException(CommonErrorCode.SMS_CODE_INVALID);
            }
        } catch (IllegalStateException ex) {
            throw new AppException(CommonErrorCode.SYSTEM_CONFIG_ERROR, "系统配置错误：未配置 Redis 缓存服务，无法进行生产环境短信校验");
        }
    }
}
