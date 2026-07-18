// 关联业务：映射实施方案锁定时的费用承担房屋、业主与面积分母快照。
package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RepairPlanAllocationRoomRow {
    private Long allocationRoomId;
    private Long planId;
    private Long tenantId;
    private Long roomId;
    private Long buildingId;
    private String unitName;
    private Long ownerUid;
    private BigDecimal buildArea;
    private LocalDateTime createTime;
}
