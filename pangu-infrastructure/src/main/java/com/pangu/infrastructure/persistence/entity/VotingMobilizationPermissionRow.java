package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class VotingMobilizationPermissionRow {

    private Long permissionId;
    private Long subjectId;
    private Long tenantId;
    private Long buildingId;
    private Long userId;
    private String roleKey;
    private Boolean canRemind;
    private Boolean canOfflineProxy;
    private Instant activatedAt;
    private Instant expiresAt;
    private Instant deactivatedAt;
    private Integer status;
}
