package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.BuildingOccupant;

/**
 * 单个楼栋占用者响应 DTO（{@link BuildingOccupancyResponse} 的元素）。
 */
public record BuildingOccupantResponse(
        Long userId,
        String nickName,
        String roleKey) {

    public static BuildingOccupantResponse from(BuildingOccupant o) {
        return new BuildingOccupantResponse(o.userId(), o.nickName(), o.roleKey());
    }
}
