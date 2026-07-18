// 关联业务：映射维修验收规则快照及其楼栋业主人数门槛。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairAcceptancePolicyRow {
    private Long policyId;
    private Long workOrderId;
    private Long tenantId;
    private String workflowType;
    private String policyHash;
    private Integer affectedOwnerCount;
    private Integer minimumAffectedOwnerParticipants;
    private Integer minimumAffectedOwnerApprovals;
    private Long lockedByUserId;
    private LocalDateTime lockedAt;
}
