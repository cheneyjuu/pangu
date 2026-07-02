package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairWorkOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RepairWorkOrderResponse(
        Long workOrderId,
        String orderNo,
        Long tenantId,
        String title,
        String description,
        String source,
        String spaceScope,
        String status,
        Long reporterAccountId,
        Long reporterUid,
        Long reporterUserId,
        Long roomId,
        Long buildingId,
        String locationText,
        boolean needManualLocation,
        boolean locationLocked,
        Long assignedUserId,
        String assigneeRoleKey,
        Long assigneeDeptId,
        String category,
        String riskLevel,
        String surveySummary,
        BigDecimal planBudget,
        String fundSource,
        boolean fundGateBlocked,
        Integer satisfactionScore,
        String satisfactionComment,
        Long version,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public static RepairWorkOrderResponse from(RepairWorkOrder w) {
        return new RepairWorkOrderResponse(
                w.workOrderId(),
                w.orderNo(),
                w.tenantId(),
                w.title(),
                w.description(),
                w.source().name(),
                w.spaceScope().name(),
                w.status().name(),
                w.reporterAccountId(),
                w.reporterUid(),
                w.reporterUserId(),
                w.roomId(),
                w.buildingId(),
                w.locationText(),
                w.needManualLocation(),
                w.locationLocked(),
                w.assignedUserId(),
                w.assigneeRoleKey(),
                w.assigneeDeptId(),
                w.category(),
                w.riskLevel(),
                w.surveySummary(),
                w.planBudget(),
                w.fundSource(),
                w.fundGateBlocked(),
                w.satisfactionScore(),
                w.satisfactionComment(),
                w.version(),
                w.createTime(),
                w.updateTime());
    }
}
