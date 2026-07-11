package com.pangu.interfaces.web.controller.dto.assembly;

import com.pangu.domain.model.assembly.OwnersAssemblySession;

import java.time.Instant;

public record OwnersAssemblySessionResponse(
        Long sessionId,
        Long tenantId,
        String title,
        String preparationMode,
        String status,
        Long createdByUserId,
        Instant createTime
) {
    public static OwnersAssemblySessionResponse from(OwnersAssemblySession session) {
        return new OwnersAssemblySessionResponse(
                session.sessionId(),
                session.tenantId(),
                session.title(),
                session.preparationMode(),
                session.status(),
                session.createdByUserId(),
                session.createTime());
    }
}
