package com.pangu.domain.model.user;

/**
 * 当前工作身份负责的楼栋摘要，供移动工作台展示责任田概览。
 */
public record AssignedBuildingSummary(
        Long buildingId,
        int unitCount,
        Double reminderCompletionRate) {
}
