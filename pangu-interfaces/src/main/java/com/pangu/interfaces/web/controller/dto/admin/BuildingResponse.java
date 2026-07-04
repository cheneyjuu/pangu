package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.WorkIdentityBuildingScope;

/**
 * 楼栋响应 DTO。
 *
 * <p>{@code buildingId} 不是全系统唯一；跨小区网格必须携带 {@code tenantId}。
 */
public record BuildingResponse(Long tenantId, Long buildingId) {

    public static BuildingResponse of(Long buildingId) {
        return new BuildingResponse(null, buildingId);
    }

    public static BuildingResponse from(WorkIdentityBuildingScope scope) {
        return new BuildingResponse(scope.tenantId(), scope.buildingId());
    }
}
