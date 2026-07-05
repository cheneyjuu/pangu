package com.pangu.infrastructure.repository;

import com.pangu.domain.repository.SmsVerificationCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisSmsVerificationCodeRepository implements SmsVerificationCodeRepository {

    private static final String KEY_PREFIX = "sms:code:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean consumeIfMatches(String phone, String smsCode) {
        if (redisTemplate == null) {
            throw new IllegalStateException("Redis cache is not configured");
        }
        String key = KEY_PREFIX + phone;
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null || !cachedCode.equals(smsCode)) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }
}
