package com.pangu.infrastructure.persistence.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 业主名册详情页 · 房产逐条行 → {@code OwnerPropertyDetail} 中转。
 */
@Data
public class OwnerPropertyDetailRow {

    private Long opid;
    private Long tenantId;
    private String communityName;
    private Long buildingId;
    private String buildingName;
    private String unitName;
    private Long roomId;
    private String roomName;
    private BigDecimal buildArea;
    private Integer jointOwnership;
    private Integer votingDelegate;   // 0/1 → boolean 由 gateway 映射
    private Integer accountStatus;
    private String verifyType;
    private String verifyStatus;
}
