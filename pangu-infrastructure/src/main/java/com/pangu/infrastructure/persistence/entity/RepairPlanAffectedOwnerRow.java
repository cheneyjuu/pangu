// 关联业务：映射实施方案固化的受影响业主房屋、身份、原因与名单来源。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairPlanAffectedOwnerRow {
    private Long planAffectedOwnerId;
    private Long planId;
    private Long tenantId;
    private Long roomId;
    private Long buildingId;
    private String buildingName;
    private String unitName;
    private String roomName;
    private Long ownerUid;
    private String affectedReason;
    private String sourceType;
    private LocalDateTime createTime;
}
