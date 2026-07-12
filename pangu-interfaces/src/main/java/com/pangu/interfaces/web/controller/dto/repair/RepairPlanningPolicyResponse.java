package com.pangu.interfaces.web.controller.dto.repair;

import com.pangu.application.repair.RepairPlanningPolicy;

public record RepairPlanningPolicyResponse(
        boolean internalEstimateRequired,
        String buildingRepairDefaultDecisionChannel
) {

    public static RepairPlanningPolicyResponse from(RepairPlanningPolicy policy) {
        return new RepairPlanningPolicyResponse(
                policy.internalEstimateRequired(),
                policy.buildingRepairDefaultDecisionChannel());
    }
}
