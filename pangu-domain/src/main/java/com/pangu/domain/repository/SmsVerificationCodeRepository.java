package com.pangu.domain.repository;

public interface SmsVerificationCodeRepository {

    boolean consumeIfMatches(String phone, String smsCode);
}
