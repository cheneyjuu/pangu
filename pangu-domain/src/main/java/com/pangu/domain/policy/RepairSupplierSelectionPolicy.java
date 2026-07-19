// 关联业务：统一维修询价和非竞争定商的最低证据规则。
package com.pangu.domain.policy;

import com.pangu.domain.model.repair.RepairProjectGovernance.SupplierSelectionEvaluationRule;
import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;

public interface RepairSupplierSelectionPolicy {

    Decision validateTerms(Terms terms);

    Decision evaluate(Input input);

    /** 授权提案中必须冻结的施工单位选择条件，不包含尚未发生的具体中选结果。 */
    record Terms(
            RepairSupplierSelectionMethod method,
            SupplierSelectionEvaluationRule evaluationRule,
            Integer minimumInvitedSupplierCount,
            Integer minimumValidQuoteCount,
            String nonCompetitiveSelectionBasis
    ) {
    }

    record Input(
            RepairSupplierSelectionMethod method,
            SupplierSelectionEvaluationRule evaluationRule,
            Integer minimumInvitedSupplierCount,
            Integer minimumValidQuoteCount,
            String nonCompetitiveSelectionBasis,
            int invitationCount,
            int validQuoteCount,
            String selectionRationale,
            boolean selectionEvidencePresent,
            boolean frameworkRelationValid
    ) {
    }

    record Decision(boolean allowed, String rejectionReason) {

        public static Decision allow() {
            return new Decision(true, null);
        }

        public static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }
}
