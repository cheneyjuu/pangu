package com.pangu.interfaces.web.service;

import com.pangu.interfaces.web.controller.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 针对生产环境的真实短信验证码校验策略 (基于 Redis 缓存服务)
 */
@Component
@Profile("prod")
public class ProdSmsVerificationStrategy implements SmsVerificationStrategy {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    public void validate(String phone, String smsCode) {
        if (smsCode == null || smsCode.isEmpty()) {
            throw new AppException(401, "认证失败：短信验证码不能为空");
        }

        if (redisTemplate == null) {
            throw new AppException(500, "系统配置错误：未配置 Redis 缓存服务，无法进行生产环境短信校验");
        }

        // 从 Redis 中获取特定手机号绑定的短信验证码
        String cachedCode = redisTemplate.opsForValue().get("sms:code:" + phone);
        if (cachedCode == null || !cachedCode.equals(smsCode)) {
            throw new AppException(401, "认证失败：短信验证码无效或已过期");
        }

        // 校验成功后将验证码作废，防止二次使用攻击
        redisTemplate.delete("sms:code:" + phone);
    }
}
