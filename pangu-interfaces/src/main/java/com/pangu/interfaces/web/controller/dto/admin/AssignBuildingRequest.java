package com.pangu.interfaces.web.controller.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 楼栋责任田分配请求 DTO。
 *
 * <p>{@code targetRoleKey} 受 application 层白名单二次校验（GRID_OPERATOR /
 * VOLUNTEER / OWNER_REPRESENTATIVE 三选一）。
 */
public record AssignBuildingRequest(
        @NotNull Long buildingId,
        @NotBlank
        @Pattern(regexp = "GRID_OPERATOR|VOLUNTEER|OWNER_REPRESENTATIVE",
                message = "targetRoleKey 必须为 GRID_OPERATOR/VOLUNTEER/OWNER_REPRESENTATIVE")
        String targetRoleKey) {}
