package com.pangu.application.repair;

public record RepairPlanningPolicy(
        boolean internalEstimateRequired,
        String buildingRepairDefaultDecisionChannel
) {
}
