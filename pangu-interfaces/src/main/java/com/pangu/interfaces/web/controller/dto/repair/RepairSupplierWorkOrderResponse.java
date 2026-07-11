package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.domain.model.repair.RepairWorkOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RepairSupplierWorkOrderResponse(
        Long workOrderId,
        String orderNo,
        String title,
        String description,
        String spaceScope,
        String status,
        Long buildingId,
        String locationText,
        String category,
        String surveySummary,
        BigDecimal publicCeilingPrice,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public static RepairSupplierWorkOrderResponse from(RepairWorkOrder workOrder) {
        return new RepairSupplierWorkOrderResponse(
                workOrder.workOrderId(),
                workOrder.orderNo(),
                workOrder.title(),
                workOrder.description(),
                workOrder.spaceScope().name(),
                workOrder.status().name(),
                workOrder.buildingId(),
                workOrder.locationText(),
                workOrder.category(),
                workOrder.surveySummary(),
                workOrder.publicCeilingPrice(),
                workOrder.createTime(),
                workOrder.updateTime());
    }
}
