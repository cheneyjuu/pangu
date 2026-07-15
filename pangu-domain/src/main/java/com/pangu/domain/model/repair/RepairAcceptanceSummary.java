// 关联业务：汇总维修验收参与人事实，供楼栋流程或全小区流程分别定案。
package com.pangu.domain.model.repair;

public record RepairAcceptanceSummary(
        int affectedOwnerCount,
        int participatingAffectedOwnerCount,
        int passedAffectedOwnerCount,
        int rectificationCount,
        boolean buildingLeaderPassed,
        boolean committeeExecutivePassed,
        boolean committeeSealApplied,
        boolean propertyTechnicalCosigned,
        boolean thirdPartyTechnicalCosigned
) {
}
