package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.BuildingAssignment;

import java.time.Instant;

/**
 * 楼栋分配响应 DTO（某用户已生效楼栋列表）。
 */
public record BuildingAssignmentResponse(
        Long assignmentId,
        Long userId,
        Long buildingId,
        Long tenantId,
        Long assignedBy,
        Instant assignedAt,
        int status) {

    public static BuildingAssignmentResponse from(BuildingAssignment a) {
        return new BuildingAssignmentResponse(
                a.assignmentId(),
                a.userId(),
                a.buildingId(),
                a.tenantId(),
                a.assignedBy(),
                a.assignedAt(),
                a.status());
    }
}
