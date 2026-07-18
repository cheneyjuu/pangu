// 关联业务：统一维修询价和非竞争定商的最低证据规则。
package com.pangu.domain.policy;

import com.pangu.domain.model.repair.RepairSupplierSelectionMethod;

@FunctionalInterface
public interface RepairSupplierSelectionPolicy {

    Decision evaluate(Input input);

    record Input(
            RepairSupplierSelectionMethod method,
            int invitationCount,
            int quoteCount,
            String recommendationReason,
            String insufficientQuoteReason,
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
