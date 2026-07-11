package com.pangu.interfaces.web.service;

import com.pangu.domain.repository.SmsVerificationCodeRepository;
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
    public boolean verify(String phone, String smsCode) {
        if (smsCode == null || smsCode.isBlank()) {
            return false;
        }
        try {
            return smsVerificationCodeRepository.consumeIfMatches(phone, smsCode);
        } catch (IllegalStateException ex) {
            throw new IllegalStateException("未配置 Redis 缓存服务，无法进行生产环境短信校验", ex);
        }
    }
}
