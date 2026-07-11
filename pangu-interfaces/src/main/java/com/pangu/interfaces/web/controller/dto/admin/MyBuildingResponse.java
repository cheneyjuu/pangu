package com.pangu.interfaces.web.controller.dto.admin;

import com.pangu.domain.model.user.AssignedBuildingSummary;

/**
 * 移动工作端「我的责任田」楼栋摘要。
 */
public record MyBuildingResponse(
        Long buildingId,
        int unitCount,
        Double reminderCompletionRate) {

    public static MyBuildingResponse from(AssignedBuildingSummary building) {
        return new MyBuildingResponse(
                building.buildingId(),
                building.unitCount(),
                building.reminderCompletionRate());
    }
}
