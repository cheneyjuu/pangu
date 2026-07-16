// 关联业务：根据维修范围与资金账簿确定费用承担规则，禁止由客户端自由填写分摊口径。
package com.pangu.domain.policy;

import com.pangu.domain.model.repair.RepairProject.AllocationRuleType;
import com.pangu.domain.model.repair.RepairProject.FundSource;
import com.pangu.domain.model.repair.RepairProject.ScopeType;

/** 将地区维修资金规则与项目编排解耦，应用层只消费不可变的规则解析结果。 */
@FunctionalInterface
public interface RepairAllocationPolicy {

    AllocationDecision resolve(AllocationInput input);

    record AllocationInput(
            ScopeType scopeType,
            FundSource fundSource,
            String scopeLabel
    ) {
    }

    record AllocationDecision(
            boolean supported,
            AllocationRuleType ruleType,
            String ruleDescription,
            String legalBasis,
            String rejectionReason
    ) {

        public static AllocationDecision support(
                AllocationRuleType ruleType,
                String ruleDescription,
                String legalBasis) {
            return new AllocationDecision(true, ruleType, ruleDescription, legalBasis, null);
        }

        public static AllocationDecision reject(String reason) {
            return new AllocationDecision(false, null, null, null, reason);
        }
    }
}
