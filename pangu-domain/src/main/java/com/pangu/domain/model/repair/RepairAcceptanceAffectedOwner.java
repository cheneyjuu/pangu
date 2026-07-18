// 关联业务：冻结楼栋维修受影响业主、房屋和受影响原因。
package com.pangu.domain.model.repair;

public record RepairAcceptanceAffectedOwner(
        Long roomId,
        Long ownerUid,
        String affectedReason
) {
}
