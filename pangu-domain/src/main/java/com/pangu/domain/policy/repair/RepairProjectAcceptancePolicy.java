// 关联业务：按锁定实施方案中的验收参与方和通过条件判断工程验收结论。
package com.pangu.domain.policy.repair;

import com.pangu.domain.model.repair.RepairAcceptanceDecision;
import com.pangu.domain.model.repair.RepairProject.AffectedOwnerPassRule;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptancePolicy;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceRequirement;
import com.pangu.domain.model.repair.RepairProjectExecution.AcceptanceSummary;
import com.pangu.domain.model.repair.RepairAcceptancePartyRole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public interface RepairProjectAcceptancePolicy {

    RepairAcceptanceDecision evaluate(AcceptancePolicy policy, AcceptanceSummary summary);

    final class Configured implements RepairProjectAcceptancePolicy {

        @Override
        public RepairAcceptanceDecision evaluate(AcceptancePolicy policy, AcceptanceSummary summary) {
            if (summary.rectificationCount() > 0) {
                return RepairAcceptanceDecision.rectificationRequired("已有验收参与方提出整改要求");
            }
            List<String> unmetConditions = new ArrayList<>();
            boolean affectedOwnerRequirementPresent = false;
            for (AcceptanceRequirement requirement : policy.requirements()) {
                if (summary.passedCount(requirement.eligibleRoles()) < requirement.minimumPassingCount()) {
                    unmetConditions.add(requirement.businessName() + "尚未达到通过条件");
                }
                if (requirement.eligibleRoles().contains(RepairAcceptancePartyRole.AFFECTED_OWNER)) {
                    affectedOwnerRequirementPresent = true;
                }
            }
            if (affectedOwnerRequirementPresent) {
                RepairAcceptanceDecision ownerDecision = evaluateAffectedOwners(policy, summary);
                if (ownerDecision.outcome() != RepairAcceptanceDecision.Outcome.PASSED) {
                    unmetConditions.add(ownerDecision.reason());
                }
            }
            if (!unmetConditions.isEmpty()) {
                return RepairAcceptanceDecision.incomplete(String.join("；", unmetConditions));
            }
            return RepairAcceptanceDecision.passed();
        }

        private RepairAcceptanceDecision evaluateAffectedOwners(
                AcceptancePolicy policy, AcceptanceSummary summary) {
            if (summary.participatingAffectedOwnerCount() < policy.minimumAffectedOwnerParticipants()) {
                return RepairAcceptanceDecision.incomplete("受影响业主未达到方案约定的最低验收人数");
            }
            if (policy.affectedOwnerPassRule() == AffectedOwnerPassRule.ALL
                    && summary.passedAffectedOwnerCount() != summary.participatingAffectedOwnerCount()) {
                return RepairAcceptanceDecision.incomplete("参加验收的受影响业主尚未全部通过");
            }
            if (policy.affectedOwnerPassRule() == AffectedOwnerPassRule.AT_LEAST_RATIO) {
                BigDecimal actual = BigDecimal.valueOf(summary.passedAffectedOwnerCount())
                        .divide(BigDecimal.valueOf(summary.participatingAffectedOwnerCount()), 8, RoundingMode.HALF_UP);
                if (actual.compareTo(policy.affectedOwnerApprovalRatio()) < 0) {
                    return RepairAcceptanceDecision.incomplete("受影响业主同意比例未达到方案约定");
                }
            }
            return RepairAcceptanceDecision.passed();
        }
    }
}
