// 关联业务：执行楼栋/单元维修由楼组长和受影响业主按项目锁定门槛验收的规则。
package com.pangu.domain.policy.repair;

import com.pangu.domain.model.repair.RepairAcceptanceDecision;
import com.pangu.domain.model.repair.RepairAcceptancePolicySnapshot;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;
import com.pangu.domain.model.repair.RepairWorkflowType;

public final class BuildingRepairAcceptanceAuthorityPolicy implements RepairAcceptanceAuthorityPolicy {

    @Override
    public RepairWorkflowType workflowType() {
        return RepairWorkflowType.BUILDING_REPAIR;
    }

    @Override
    public RepairAcceptanceDecision evaluate(
            RepairAcceptancePolicySnapshot snapshot,
            RepairAcceptanceSummary summary) {
        requireWorkflow(snapshot);
        if (summary.rectificationCount() > 0) {
            return RepairAcceptanceDecision.rectificationRequired("受影响业主或楼组长已明确要求整改");
        }
        if (!summary.buildingLeaderPassed()) {
            return RepairAcceptanceDecision.incomplete("楼组长尚未验收通过");
        }
        if (summary.participatingAffectedOwnerCount() < snapshot.minimumAffectedOwnerParticipants()) {
            return RepairAcceptanceDecision.incomplete("受影响业主未达到项目锁定的最低有效验收人数");
        }
        if (summary.passedAffectedOwnerCount() < snapshot.minimumAffectedOwnerApprovals()) {
            return RepairAcceptanceDecision.incomplete("受影响业主同意数未达到项目锁定的通过门槛");
        }
        return RepairAcceptanceDecision.passed();
    }

    private void requireWorkflow(RepairAcceptancePolicySnapshot snapshot) {
        if (snapshot.workflowType() != workflowType()) {
            throw new IllegalArgumentException("building acceptance policy received another workflow");
        }
    }
}
