// 关联业务：表示一次完工验收或整改复验轮次。
package com.pangu.domain.model.repair;

public record RepairAcceptanceRound(
        Long acceptanceId,
        Long workOrderId,
        Long tenantId,
        Long policyId,
        int roundNo,
        String status
) {
}
