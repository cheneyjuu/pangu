package com.pangu.interfaces.web.controller.dto;

/**
 * 登录请求 DTO
 */
public class LoginRequest {
    private String username;
    private String smsCode;
    private Integer loginType;
    private String clientPortal;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSmsCode() {
        return smsCode;
    }

    public void setSmsCode(String smsCode) {
        this.smsCode = smsCode;
    }

    public Integer getLoginType() {
        return loginType;
    }

    public void setLoginType(Integer loginType) {
        this.loginType = loginType;
    }

    public String getClientPortal() {
        return clientPortal;
    }

    public void setClientPortal(String clientPortal) {
        this.clientPortal = clientPortal;
    }
}
