package com.pangu.interfaces.web.controller.dto.admin;

/**
 * 楼栋响应 DTO。
 *
 * <p>无独立楼栋表，{@code buildingId} 来自 {@code c_owner_property.building_id} 的
 * distinct 值；前端展示「楼栋 #{buildingId}」。
 */
public record BuildingResponse(Long buildingId) {

    public static BuildingResponse of(Long buildingId) {
        return new BuildingResponse(buildingId);
    }
}
