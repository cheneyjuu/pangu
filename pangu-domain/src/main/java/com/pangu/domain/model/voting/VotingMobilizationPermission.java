package com.pangu.domain.model.voting;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 投票期事件驱动动员权限。
 */
@Data
@Builder
public class VotingMobilizationPermission {

    private Long permissionId;
    private Long subjectId;
    private Long tenantId;
    private Long buildingId;
    private Long userId;
    private String roleKey;
    private boolean canRemind;
    private boolean canOfflineProxy;
    private Instant activatedAt;
    private Instant expiresAt;
    private Instant deactivatedAt;
    private int status;

    public boolean activeAt(Instant now) {
        if (status != 1 || deactivatedAt != null) {
            return false;
        }
        return expiresAt == null || now == null || expiresAt.isAfter(now);
    }
}
