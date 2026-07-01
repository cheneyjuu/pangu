package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.BuildingOccupancy;

import java.util.List;

/**
 * 楼栋占用快照响应 DTO（楼栋分配页占用展示用）。
 *
 * <p>{@code occupants} 含不同角色的占用者，前端按 roleKey 分组识别同角色冲突。
 */
public record BuildingOccupancyResponse(
        Long buildingId,
        List<BuildingOccupantResponse> occupants) {

    public static BuildingOccupancyResponse from(BuildingOccupancy o) {
        return new BuildingOccupancyResponse(
                o.buildingId(),
                o.occupants().stream().map(BuildingOccupantResponse::from).toList());
    }
}
