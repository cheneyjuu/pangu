package com.pangu.domain.model.repair;

/** 验收定案所需的后端聚合事实。 */
public record RepairAcceptanceSummary(
        int affectedRoomCount,
        int passedAffectedRoomCount,
        int rectificationCount,
        int unreachableCount,
        boolean ownerRepresentativePassed,
        boolean propertyRepresentativePassed
) {
}
