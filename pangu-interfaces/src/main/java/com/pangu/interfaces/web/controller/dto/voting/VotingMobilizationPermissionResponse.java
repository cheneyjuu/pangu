package com.pangu.interfaces.web.controller.dto.voting;

import com.pangu.domain.model.voting.VotingMobilizationPermission;

import java.time.Instant;

public record VotingMobilizationPermissionResponse(
        Long permissionId,
        Long subjectId,
        Long buildingId,
        String roleKey,
        boolean canRemind,
        boolean canOfflineProxy,
        Instant activatedAt,
        Instant expiresAt
) {
    public static VotingMobilizationPermissionResponse from(VotingMobilizationPermission permission) {
        return new VotingMobilizationPermissionResponse(
                permission.getPermissionId(),
                permission.getSubjectId(),
                permission.getBuildingId(),
                permission.getRoleKey(),
                permission.isCanRemind(),
                permission.isCanOfflineProxy(),
                permission.getActivatedAt(),
                permission.getExpiresAt());
    }
}
