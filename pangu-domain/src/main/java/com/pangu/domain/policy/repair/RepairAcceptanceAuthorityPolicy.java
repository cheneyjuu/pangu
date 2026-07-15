// 关联业务：按维修流程类型校验业主侧验收权，不允许楼栋人员与业委会互相替代。
package com.pangu.domain.policy.repair;

import com.pangu.domain.model.repair.RepairAcceptanceDecision;
import com.pangu.domain.model.repair.RepairAcceptancePolicySnapshot;
import com.pangu.domain.model.repair.RepairAcceptanceSummary;
import com.pangu.domain.model.repair.RepairWorkflowType;

public interface RepairAcceptanceAuthorityPolicy {

    RepairWorkflowType workflowType();

    RepairAcceptanceDecision evaluate(
            RepairAcceptancePolicySnapshot snapshot,
            RepairAcceptanceSummary summary);
}
