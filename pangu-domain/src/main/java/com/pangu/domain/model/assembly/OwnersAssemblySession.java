package com.pangu.domain.model.assembly;

import java.time.Instant;

/** 一次业主大会。 */
public record OwnersAssemblySession(
        Long sessionId,
        Long tenantId,
        String title,
        String preparationMode,
        String status,
        Long createdByUserId,
        Instant createTime
) {
}
