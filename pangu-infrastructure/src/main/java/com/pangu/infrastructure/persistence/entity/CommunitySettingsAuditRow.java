package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class CommunitySettingsAuditRow {
    private Long auditId;
    private Long tenantId;
    private String operationType;
    private Long operatorUserId;
    private Instant createTime;
}
