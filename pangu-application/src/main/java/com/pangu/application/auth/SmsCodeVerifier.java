package com.pangu.application.auth;

/**
 * 一次性短信验证码校验端口。
 *
 * <p>生产实现必须在校验成功时消费验证码，防止同一验证码被重复用于登录或账号激活。
 */
@FunctionalInterface
public interface SmsCodeVerifier {

    boolean verify(String phone, String smsCode);
}
