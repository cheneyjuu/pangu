// 关联业务：提交维修方案，并在征询或邀价前冻结对应流程的验收规则。
package com.pangu.application.repair.command;

import java.math.BigDecimal;
import java.util.List;

public record SubmitRepairPlanCommand(
        BigDecimal planBudget,
        BigDecimal publicCeilingPrice,
        String fundSource,
        AcceptancePolicy acceptancePolicy,
        String remark
) {
    public record AcceptancePolicy(
            List<AffectedOwner> affectedOwners,
            Integer minimumAffectedOwnerParticipants,
            Integer minimumAffectedOwnerApprovals
    ) {
    }

    public record AffectedOwner(
            Long roomId,
            Long ownerUid,
            String affectedReason
    ) {
    }
}
