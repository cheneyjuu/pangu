package com.pangu.interfaces.web.controller.dto;

/**
 * 管理端切换工作分身请求 DTO。
 */
public class SwitchShadowRequest {
    private Long targetUserId;

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }
}
