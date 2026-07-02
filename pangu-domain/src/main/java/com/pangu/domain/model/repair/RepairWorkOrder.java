package com.pangu.domain.model.repair;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 维修报修工单读写模型。 */
public record RepairWorkOrder(
        Long workOrderId,
        String orderNo,
        Long tenantId,
        String title,
        String description,
        RepairSource source,
        RepairSpaceScope spaceScope,
        RepairWorkOrderStatus status,
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

    public RepairWorkOrder withStatus(RepairWorkOrderStatus nextStatus,
                                      boolean nextNeedManualLocation,
                                      boolean nextLocationLocked,
                                      boolean nextFundGateBlocked) {
        return new RepairWorkOrder(workOrderId, orderNo, tenantId, title, description, source,
                spaceScope, nextStatus, reporterAccountId, reporterUid, reporterUserId,
                roomId, buildingId, locationText, nextNeedManualLocation, nextLocationLocked,
                assignedUserId, assigneeRoleKey, assigneeDeptId, category, riskLevel,
                surveySummary, planBudget, fundSource, nextFundGateBlocked,
                satisfactionScore, satisfactionComment, version, createTime, updateTime);
    }

    public RepairWorkOrder withLocation(Long nextBuildingId,
                                        Long nextRoomId,
                                        String nextLocationText,
                                        RepairWorkOrderStatus nextStatus,
                                        boolean nextNeedManualLocation,
                                        boolean nextLocationLocked,
                                        boolean nextFundGateBlocked) {
        return new RepairWorkOrder(workOrderId, orderNo, tenantId, title, description, source,
                spaceScope, nextStatus, reporterAccountId, reporterUid, reporterUserId,
                nextRoomId, nextBuildingId, nextLocationText, nextNeedManualLocation,
                nextLocationLocked, assignedUserId, assigneeRoleKey, assigneeDeptId,
                category, riskLevel, surveySummary, planBudget, fundSource, nextFundGateBlocked,
                satisfactionScore, satisfactionComment, version, createTime, updateTime);
    }

    public RepairWorkOrder withAssignment(Long nextAssignedUserId,
                                          String nextAssigneeRoleKey,
                                          Long nextAssigneeDeptId,
                                          RepairWorkOrderStatus nextStatus) {
        return new RepairWorkOrder(workOrderId, orderNo, tenantId, title, description, source,
                spaceScope, nextStatus, reporterAccountId, reporterUid, reporterUserId,
                roomId, buildingId, locationText, needManualLocation, locationLocked,
                nextAssignedUserId, nextAssigneeRoleKey, nextAssigneeDeptId, category,
                riskLevel, surveySummary, planBudget, fundSource, fundGateBlocked,
                satisfactionScore, satisfactionComment, version, createTime, updateTime);
    }

    public RepairWorkOrder withPlan(String nextSurveySummary,
                                    String nextRiskLevel,
                                    BigDecimal nextPlanBudget,
                                    String nextFundSource,
                                    RepairWorkOrderStatus nextStatus) {
        return new RepairWorkOrder(workOrderId, orderNo, tenantId, title, description, source,
                spaceScope, nextStatus, reporterAccountId, reporterUid, reporterUserId,
                roomId, buildingId, locationText, needManualLocation, locationLocked,
                assignedUserId, assigneeRoleKey, assigneeDeptId, category, nextRiskLevel,
                nextSurveySummary, nextPlanBudget, nextFundSource, fundGateBlocked,
                satisfactionScore, satisfactionComment, version, createTime, updateTime);
    }

    public RepairWorkOrder withEvaluation(Integer nextSatisfactionScore,
                                          String nextSatisfactionComment,
                                          RepairWorkOrderStatus nextStatus) {
        return new RepairWorkOrder(workOrderId, orderNo, tenantId, title, description, source,
                spaceScope, nextStatus, reporterAccountId, reporterUid, reporterUserId,
                roomId, buildingId, locationText, needManualLocation, locationLocked,
                assignedUserId, assigneeRoleKey, assigneeDeptId, category, riskLevel,
                surveySummary, planBudget, fundSource, fundGateBlocked,
                nextSatisfactionScore, nextSatisfactionComment, version, createTime, updateTime);
    }
}
