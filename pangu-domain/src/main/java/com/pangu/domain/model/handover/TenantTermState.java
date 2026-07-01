package com.pangu.domain.model.handover;

import java.time.Instant;

/**
 * 租户任期状态快照。
 */
public record TenantTermState(
        Long tenantId,
        TenantTermStatus status,
        Long lockedBySubjectId,
        Instant lockedAt,
        Instant unlockedAt,
        Long unlockedByUserId) {

    public boolean locked() {
        return status == TenantTermStatus.HANDOVER_LOCK;
    }
}
