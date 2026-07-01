package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class TenantTermStateRow {

    private Long tenantId;
    private Integer termStatus;
    private Instant termLockedAt;
    private Long termLockedBySubjectId;
    private Instant termUnlockedAt;
    private Long termUnlockedByUserId;
}
