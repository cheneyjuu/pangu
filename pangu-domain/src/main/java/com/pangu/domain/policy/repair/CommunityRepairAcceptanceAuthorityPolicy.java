// 关联业务：执行全小区公共维修的业委会验收三项强制条件。
package com.pangu.domain.policy.repair;

import com.pangu.domain.model.repair.RepairAcceptanceDecision;
import com.pangu.domain.model.repair.RepairAcceptancePolicySnapshot;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;
import com.pangu.domain.model.repair.RepairWorkflowType;

public final class CommunityRepairAcceptanceAuthorityPolicy implements RepairAcceptanceAuthorityPolicy {

    @Override
    public RepairWorkflowType workflowType() {
        return RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR;
    }

    @Override
    public RepairAcceptanceDecision evaluate(
            RepairAcceptancePolicySnapshot snapshot,
            RepairAcceptanceSummary summary) {
        requireWorkflow(snapshot);
        if (summary.rectificationCount() > 0) {
            return RepairAcceptanceDecision.rectificationRequired("业委会或专业共同签署人已明确要求整改");
        }
        if (!summary.committeeExecutivePassed()) {
            return RepairAcceptanceDecision.incomplete("业委会主任或副主任尚未在线同意");
        }
        if (!summary.committeeSealApplied()) {
            return RepairAcceptanceDecision.incomplete("验收文件尚未完成业委会公章用印及登记");
        }
        if (!summary.propertyTechnicalCosigned() && !summary.thirdPartyTechnicalCosigned()) {
            return RepairAcceptanceDecision.incomplete("物业或第三方专业人员尚未共同签署");
        }
        return RepairAcceptanceDecision.passed();
    }

    private void requireWorkflow(RepairAcceptancePolicySnapshot snapshot) {
        if (snapshot.workflowType() != workflowType()) {
            throw new IllegalArgumentException("community acceptance policy received another workflow");
        }
    }
}
