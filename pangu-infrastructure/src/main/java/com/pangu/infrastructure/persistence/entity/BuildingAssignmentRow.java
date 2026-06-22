package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.Instant;

/**
 * {@code sys_user_building} 行映射。
 *
 * <p>{@code status} 1=生效, 2=已撤销；撤销后可复活（{@code BuildingAssignmentRepositoryImpl.assign}）。
 */
@Data
public class BuildingAssignmentRow {
    private Long assignmentId;
    private Long userId;
    private Long buildingId;
    private Long tenantId;
    private Long assignedBy;
    private Instant assignedAt;
    private Integer status;
    private String revokeReason;
}
