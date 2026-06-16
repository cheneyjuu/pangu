package com.pangu.interfaces.web.controller.dto;

/**
 * 切换租户请求 DTO
 */
public class SwitchTenantRequest {
    private Long targetTenantId;

    public Long getTargetTenantId() {
        return targetTenantId;
    }

    public void setTargetTenantId(Long targetTenantId) {
        this.targetTenantId = targetTenantId;
    }
}
