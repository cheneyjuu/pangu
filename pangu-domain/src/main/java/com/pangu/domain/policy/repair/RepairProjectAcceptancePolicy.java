// 关联业务：按维修项目的独立流程校验楼栋业主验收或全小区业委会验收的不可跳过条件。
package com.pangu.domain.policy.repair;

import com.pangu.domain.model.repair.RepairAcceptanceDecision;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptancePolicy;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceSummary;
import com.pangu.domain.model.repair.RepairWorkflowType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface RepairProjectAcceptancePolicy {

    RepairWorkflowType workflowType();

    RepairAcceptanceDecision evaluate(AcceptancePolicy policy, AcceptanceSummary summary);

    final class Building implements RepairProjectAcceptancePolicy {

        @Override
        public RepairWorkflowType workflowType() {
            return RepairWorkflowType.BUILDING_REPAIR;
        }

        @Override
        public RepairAcceptanceDecision evaluate(AcceptancePolicy policy, AcceptanceSummary summary) {
            requireWorkflow(policy, workflowType());
            if (summary.rectificationCount() > 0) {
                return RepairAcceptanceDecision.rectificationRequired("楼组长或受影响业主已要求整改");
            }
            if (!summary.buildingLeaderPassed()) {
                return RepairAcceptanceDecision.incomplete("楼组长尚未验收通过");
            }
            if (summary.participatingAffectedOwnerCount() < policy.minimumAffectedOwnerParticipants()) {
                return RepairAcceptanceDecision.incomplete("受影响业主未达到方案锁定的最低有效验收人数");
            }
            if (policy.affectedOwnerPassRule() == AffectedOwnerPassRule.ALL
                    && summary.passedAffectedOwnerCount() != summary.participatingAffectedOwnerCount()) {
                return RepairAcceptanceDecision.incomplete("受影响业主验收未全部通过");
            }
            if (policy.affectedOwnerPassRule() == AffectedOwnerPassRule.AT_LEAST_RATIO) {
                BigDecimal actual = BigDecimal.valueOf(summary.passedAffectedOwnerCount())
                        .divide(BigDecimal.valueOf(summary.participatingAffectedOwnerCount()), 8, RoundingMode.HALF_UP);
                if (actual.compareTo(policy.affectedOwnerApprovalRatio()) < 0) {
                    return RepairAcceptanceDecision.incomplete("受影响业主同意比例未达到方案锁定门槛");
                }
            }
            return RepairAcceptanceDecision.passed();
        }
    }

    final class Community implements RepairProjectAcceptancePolicy {

        @Override
        public RepairWorkflowType workflowType() {
            return RepairWorkflowType.COMMUNITY_PUBLIC_REPAIR;
        }

        @Override
        public RepairAcceptanceDecision evaluate(AcceptancePolicy policy, AcceptanceSummary summary) {
            requireWorkflow(policy, workflowType());
            if (summary.rectificationCount() > 0) {
                return RepairAcceptanceDecision.rectificationRequired("业委会或专业共同签署人已要求整改");
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
    }

    private static void requireWorkflow(AcceptancePolicy policy, RepairWorkflowType expected) {
        if (policy.workflowType() != expected) {
            throw new IllegalArgumentException("project acceptance policy received another workflow");
        }
    }
}
