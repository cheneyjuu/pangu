// 关联业务：依据共有范围、资金账簿和治理依据，从后端确定维修工程所属真实流程。
package com.pangu.domain.policy;

import com.pangu.domain.model.repair.RepairProject.FundSource;
import com.pangu.domain.model.repair.RepairProject.GovernancePath;
import com.pangu.domain.model.repair.RepairProject.ScopeType;
import com.pangu.domain.model.repair.RepairWorkflowType;

/** 防止客户端用 workflowType 绕过楼栋资金和全小区资金的责任边界。 */
@FunctionalInterface
public interface RepairWorkflowRoutingPolicy {

    RoutingDecision route(RoutingInput input);

    record RoutingInput(
            ScopeType scopeType,
            Long buildingId,
            String unitName,
            FundSource fundSource,
            GovernancePath governancePath
    ) {
    }

    record RoutingDecision(
            boolean allowed,
            RepairWorkflowType workflowType,
            String rejectionReason
    ) {

        public static RoutingDecision allow(RepairWorkflowType workflowType) {
            return new RoutingDecision(true, workflowType, null);
        }

        public static RoutingDecision reject(String reason) {
            return new RoutingDecision(false, null, reason);
        }
    }
}
