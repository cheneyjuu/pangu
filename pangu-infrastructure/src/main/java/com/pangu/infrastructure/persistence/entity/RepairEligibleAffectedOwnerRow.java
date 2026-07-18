// 关联业务：映射楼栋维修受影响业主候选房屋及其已核验产权代表。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

@Data
public class RepairEligibleAffectedOwnerRow {
    private Long roomId;
    private Long buildingId;
    private String buildingName;
    private String unitName;
    private String roomName;
    private Long ownerUid;
}
